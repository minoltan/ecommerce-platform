import { Duration, RemovalPolicy, Stack, StackProps } from 'aws-cdk-lib';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import { Construct } from 'constructs';

/**
 * Replaces `aws ecr create-repository --repository-name user-service` from
 * phase1/DEPLOYING_AWS.md Step 1. RemovalPolicy.DESTROY + emptyOnDelete so
 * `cdk destroy` (phase1/CICD.md teardown workflow) can actually remove it —
 * matching the guide's existing "delete the repo too if you don't need the
 * image" post-teardown checklist item, now automatic instead of a manual
 * `aws ecr delete-repository --force`.
 */
export class EcrStack extends Stack {
  public readonly userServiceRepo: ecr.Repository;

  constructor(scope: Construct, id: string, props?: StackProps) {
    super(scope, id, props);

    this.userServiceRepo = new ecr.Repository(this, 'UserServiceRepo', {
      repositoryName: 'user-service',
      imageScanOnPush: true,
      removalPolicy: RemovalPolicy.DESTROY,
      emptyOnDelete: true,
      lifecycleRules: [
        {
          description: 'Expire untagged images after 7 days',
          tagStatus: ecr.TagStatus.UNTAGGED,
          maxImageAge: Duration.days(7),
        },
        {
          description: 'Keep only the last 10 tagged images',
          tagStatus: ecr.TagStatus.TAGGED,
          tagPrefixList: ['sha-', 'v'],
          maxImageCount: 10,
        },
      ],
    });
  }
}
