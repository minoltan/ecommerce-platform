import { CfnOutput, Stack, StackProps } from 'aws-cdk-lib';
import * as iam from 'aws-cdk-lib/aws-iam';
import { Construct } from 'constructs';

export interface GithubOidcStackProps extends StackProps {
  /** e.g. "Minoltan" */
  githubOrg: string;
  /** e.g. "ecommerce-platform" */
  githubRepo: string;
}

/**
 * One-time bootstrap stack — deployed manually from a developer machine, not
 * by the pipeline itself (the pipeline's own AWS role can't create itself).
 * See phase1/infra/cdk/README.md "One-time bootstrap".
 *
 * Creates the GitHub Actions OIDC trust (no long-lived AWS access keys in
 * repo secrets, per ADR-0016 §3) and one deploy role, scoped to this repo
 * only. Permissions are scoped to what `cdk deploy` actually needs given the
 * account has run `cdk bootstrap` (assume-role onto the bootstrap qualifier's
 * deploy/publishing/lookup roles) plus ECR push and read-only EKS access —
 * not raw AdministratorAccess.
 */
export class GithubOidcStack extends Stack {
  public readonly deployRole: iam.Role;

  constructor(scope: Construct, id: string, props: GithubOidcStackProps) {
    super(scope, id, props);

    // Auto-fetches the current GitHub Actions OIDC thumbprint if not supplied.
    // NOTE: an AWS account may only have one OIDC provider per issuer URL — if
    // token.actions.githubusercontent.com is already registered (e.g. by
    // another project in this account), replace this with
    // iam.OpenIdConnectProvider.fromOpenIdConnectProviderArn(...) instead.
    const githubOidcProvider = new iam.OpenIdConnectProvider(this, 'GithubOidcProvider', {
      url: 'https://token.actions.githubusercontent.com',
      clientIds: ['sts.amazonaws.com'],
    });

    // Scoped to this repo only (any branch/PR/ref) — a personal-account trade-off
    // documented in ADR-0016; tighten to a specific `ref:refs/heads/main` StringEquals
    // condition for a shared/organisational account.
    this.deployRole = new iam.Role(this, 'GithubActionsDeployRole', {
      roleName: 'ecommerce-platform-github-actions-deploy',
      assumedBy: new iam.WebIdentityPrincipal(githubOidcProvider.openIdConnectProviderArn, {
        StringEquals: {
          'token.actions.githubusercontent.com:aud': 'sts.amazonaws.com',
        },
        StringLike: {
          'token.actions.githubusercontent.com:sub': `repo:${props.githubOrg}/${props.githubRepo}:*`,
        },
      }),
      description: 'Assumed by phase1 GitHub Actions workflows (CI synth + CD deploy/teardown) via OIDC.',
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonEC2ContainerRegistryPowerUser'),
      ],
    });

    // Standard CDK CLI credentials-plan: the deploying identity only needs to
    // assume the roles `cdk bootstrap` already created (deploy/file-publishing/
    // image-publishing/lookup) — not direct permissions on every service CDK
    // touches. Requires `cdk bootstrap aws://<account>/<region>` to have been
    // run once (README "One-time bootstrap").
    this.deployRole.addToPolicy(
      new iam.PolicyStatement({
        sid: 'AssumeCdkBootstrapRoles',
        actions: ['sts:AssumeRole'],
        resources: [`arn:aws:iam::${this.account}:role/cdk-hnb659fds-*-role-${this.account}-${this.region}`],
      }),
    );

    this.deployRole.addToPolicy(
      new iam.PolicyStatement({
        sid: 'EksKubeconfig',
        actions: ['eks:DescribeCluster', 'eks:ListClusters'],
        resources: [`arn:aws:eks:${this.region}:${this.account}:cluster/ecommerce-platform-test`],
      }),
    );

    new CfnOutput(this, 'DeployRoleArn', {
      value: this.deployRole.roleArn,
      description: 'Set this as the AWS_DEPLOY_ROLE_ARN repository variable in GitHub (see phase1/CICD.md).',
    });
  }
}
