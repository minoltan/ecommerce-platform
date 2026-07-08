#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { NetworkStack } from '../lib/network-stack';
import { EcrStack } from '../lib/ecr-stack';
import { EksStack } from '../lib/eks-stack';
import { GithubOidcStack } from '../lib/github-oidc-stack';

const app = new cdk.App();

const env: cdk.Environment = {
  account: process.env.CDK_DEFAULT_ACCOUNT,
  region: process.env.CDK_DEFAULT_REGION ?? 'ap-south-1', // matches phase1/k8s/eks-cluster.yaml
};

// Deployed once, manually, from a developer machine — see
// phase1/infra/cdk/README.md "One-time bootstrap". Not part of the
// deploy.yml pipeline's routine `cdk deploy` target list.
const githubOidcStack = new GithubOidcStack(app, 'EcommerceGithubOidcStack', {
  env,
  githubOrg: process.env.GITHUB_REPO_OWNER ?? 'Minoltan',
  githubRepo: process.env.GITHUB_REPO_NAME ?? 'ecommerce-platform',
});

// Everything below is what deploy.yml deploys on every run.
const networkStack = new NetworkStack(app, 'EcommerceNetworkStack', { env });

new EcrStack(app, 'EcommerceEcrStack', { env });

const adminPrincipalArns = [githubOidcStack.deployRole.roleArn];
if (process.env.EKS_ADDITIONAL_ADMIN_ARN) {
  adminPrincipalArns.push(process.env.EKS_ADDITIONAL_ADMIN_ARN);
}

const eksStack = new EksStack(app, 'EcommerceEksStack', {
  env,
  vpc: networkStack.vpc,
  adminPrincipalArns,
});
eksStack.addDependency(networkStack);
eksStack.addDependency(githubOidcStack);
