import { CfnJson, CfnOutput, Stack, StackProps } from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as eks from 'aws-cdk-lib/aws-eks';
import * as iam from 'aws-cdk-lib/aws-iam';
import { KubectlV30Layer } from '@aws-cdk/lambda-layer-kubectl-v30';
import { Construct } from 'constructs';

export interface EksStackProps extends StackProps {
  vpc: ec2.Vpc;
  /**
   * IAM principal ARNs (e.g. the GitHub OIDC deploy role, an individual
   * developer's IAM role for `kubectl` troubleshooting) granted cluster-admin
   * via an explicit EKS Access Entry. Kept explicit rather than relying solely
   * on EKS's implicit "cluster creator gets admin" behaviour, which is tied to
   * whichever principal happens to make the CreateCluster API call and is
   * easy to lose track of.
   */
  adminPrincipalArns: string[];
}

/**
 * Replaces the cluster/nodegroup/addons portion of phase1/k8s/eks-cluster.yaml
 * (the eksctl config). Same sizing (2x t3.medium managed node group) and same
 * addon set (vpc-cni, coredns, kube-proxy, aws-ebs-csi-driver with IRSA) — see
 * phase1/DEPLOYING_AWS.md for the resource math behind the node count/size.
 *
 * Uses EKS Access Entries (AuthenticationMode.API) instead of the legacy
 * aws-auth ConfigMap, and does not use CDK's kubectl-Lambda-backed constructs
 * (addManifest/addHelmChart/awsAuth) — per ADR-0016, CDK stops at the cluster
 * boundary; Kustomize (phase1/k8s/, phase1/user-service/k8s/) owns everything
 * inside the cluster.
 */
export class EksStack extends Stack {
  public readonly cluster: eks.Cluster;

  constructor(scope: Construct, id: string, props: EksStackProps) {
    super(scope, id, props);

    this.cluster = new eks.Cluster(this, 'EcommercePlatformCluster', {
      clusterName: 'ecommerce-platform-test',
      version: eks.KubernetesVersion.V1_30,
      vpc: props.vpc,
      vpcSubnets: [{ subnetType: ec2.SubnetType.PUBLIC }],
      defaultCapacity: 0, // node group added explicitly below, sized like eks-cluster.yaml
      kubectlLayer: new KubectlV30Layer(this, 'KubectlLayer'),
      authenticationMode: eks.AuthenticationMode.API,
      bootstrapClusterCreatorAdminPermissions: true,
      endpointAccess: eks.EndpointAccess.PUBLIC, // matches eksctl's publicAccess:true/privateAccess:false
    });

    for (const arn of props.adminPrincipalArns) {
      this.cluster.grantAccess(`AdminAccess-${hashArn(arn)}`, arn, [
        eks.AccessPolicy.fromAccessPolicyName('AmazonEKSClusterAdminPolicy', {
          accessScopeType: eks.AccessScopeType.CLUSTER,
        }),
      ]);
    }

    const nodegroup = this.cluster.addNodegroupCapacity('DefaultNodeGroup', {
      nodegroupName: 'ng-1',
      instanceTypes: [ec2.InstanceType.of(ec2.InstanceClass.T3, ec2.InstanceSize.MEDIUM)],
      amiType: eks.NodegroupAmiType.AL2023_X86_64_STANDARD,
      minSize: 2,
      maxSize: 2,
      desiredSize: 2,
      diskSize: 20,
      subnets: { subnetType: ec2.SubnetType.PUBLIC },
    });

    this.addManagedAddons();
    // Needs running nodes to schedule its DaemonSet onto, unlike vpc-cni/coredns/
    // kube-proxy which EKS already self-manages from cluster creation onward.
    this.addEbsCsiDriver(nodegroup);

    new CfnOutput(this, 'ClusterName', { value: this.cluster.clusterName });
    new CfnOutput(this, 'UpdateKubeconfigCommand', {
      value: `aws eks update-kubeconfig --name ${this.cluster.clusterName} --region ${this.region}`,
    });
  }

  /** vpc-cni, coredns, kube-proxy — declared explicitly (vs. relying on EKS's
   * implicit defaults) so version/config is visible in this stack, matching
   * eks-cluster.yaml's explicit `addons:` list. */
  private addManagedAddons(): void {
    for (const addonName of ['vpc-cni', 'coredns', 'kube-proxy']) {
      new eks.CfnAddon(this, `Addon-${addonName}`, {
        addonName,
        clusterName: this.cluster.clusterName,
        resolveConflicts: 'OVERWRITE',
      });
    }
  }

  /** IRSA role for the aws-ebs-csi-driver addon — the eksctl config's
   * `wellKnownPolicies: ebsCSIController: true` equivalent. The addon itself
   * creates/annotates its ServiceAccount when `serviceAccountRoleArn` is set,
   * so this does not need a kubectl-Lambda-backed `addServiceAccount` call. */
  private addEbsCsiDriver(nodegroup: eks.Nodegroup): void {
    const oidcProvider = this.cluster.openIdConnectProvider;

    // openIdConnectProviderIssuer is itself a CDK token (derived from the
    // provider's ARN via Fn::Select/Fn::Split), so it can't be used directly
    // as an object key — CfnJson defers that string interpolation to
    // deploy time instead of synth time.
    const oidcConditions = new CfnJson(this, 'EbsCsiOidcConditions', {
      value: {
        [`${oidcProvider.openIdConnectProviderIssuer}:sub`]:
          'system:serviceaccount:kube-system:ebs-csi-controller-sa',
        [`${oidcProvider.openIdConnectProviderIssuer}:aud`]: 'sts.amazonaws.com',
      },
    });

    const ebsCsiRole = new iam.Role(this, 'EbsCsiDriverRole', {
      assumedBy: new iam.FederatedPrincipal(
        oidcProvider.openIdConnectProviderArn,
        { StringEquals: oidcConditions },
        'sts:AssumeRoleWithWebIdentity',
      ),
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('service-role/AmazonEBSCSIDriverPolicy'),
      ],
    });

    const ebsCsiAddon = new eks.CfnAddon(this, 'Addon-aws-ebs-csi-driver', {
      addonName: 'aws-ebs-csi-driver',
      clusterName: this.cluster.clusterName,
      serviceAccountRoleArn: ebsCsiRole.roleArn,
      resolveConflicts: 'OVERWRITE',
    });
    ebsCsiAddon.node.addDependency(nodegroup);
  }
}

/** Short, stable, alphanumeric suffix for construct IDs derived from an ARN. */
function hashArn(arn: string): string {
  let hash = 0;
  for (let i = 0; i < arn.length; i++) {
    hash = (hash * 31 + arn.charCodeAt(i)) | 0;
  }
  return Math.abs(hash).toString(36);
}
