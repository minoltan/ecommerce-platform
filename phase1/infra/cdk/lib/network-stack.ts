import { Stack, StackProps, Tags } from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import { Construct } from 'constructs';

/**
 * Public-only VPC, 2 AZs, no NAT Gateway — replaces the `vpc` block in
 * phase1/k8s/eks-cluster.yaml (eksctl's `nat: gateway: Disable` /
 * `publicAccess: true, privateAccess: false`). Same cost trade-off: this is a
 * short-lived deploy/load-test/teardown environment (phase1/DEPLOYING_AWS.md),
 * so the ~$0.045/hr NAT Gateway + data-processing charge buys nothing here —
 * nodes and pods only need public IPs to reach ECR/the internet, not private
 * egress. Not a production topology (docs/hld/deployment-architecture.md §7's
 * target design has private subnets and multi-AZ node pools).
 */
export class NetworkStack extends Stack {
  public readonly vpc: ec2.Vpc;

  constructor(scope: Construct, id: string, props?: StackProps) {
    super(scope, id, props);

    this.vpc = new ec2.Vpc(this, 'EcommerceVpc', {
      ipAddresses: ec2.IpAddresses.cidr('10.42.0.0/16'),
      maxAzs: 2,
      natGateways: 0,
      subnetConfiguration: [
        {
          name: 'public',
          subnetType: ec2.SubnetType.PUBLIC,
          cidrMask: 20,
        },
      ],
    });

    Tags.of(this.vpc).add('Project', 'ecommerce-platform');
    Tags.of(this.vpc).add('Phase', 'phase1');
  }
}
