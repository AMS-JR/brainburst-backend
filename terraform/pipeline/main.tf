provider "aws" {
  region = "us-east-1"
}

# --- Variables for Configuration ---
variable "java_app_bucket_name" {
  description = "Name for the S3 bucket to store the Java JAR artifact."
  type        = string
  default     = "my-java-app-jars-unique-name-619016" # <<< IMPORTANT: CHANGE THIS TO A GLOBALLY UNIQUE NAME
}

variable "codepipeline_artifact_bucket_name" {
  description = "Name for the S3 bucket used by CodePipeline as its artifact store."
  type        = string
  default     = "my-java-pipeline-artifacts-unique-name-619016" # <<< IMPORTANT: CHANGE THIS TO A GLOBALLY UNIQUE NAME
}

variable "github_repo_owner" {
  description = "The owner of the GitHub repository (e.g., your GitHub username or organization)."
  type        = string
}

variable "github_repo_name" {
  description = "The name of the GitHub repository containing your Java application."
  type        = string
}

variable "github_repo_branch" {
  description = "The branch of the GitHub repository to build."
  type        = string
  default     = "main" # Common default, adjust if your main branch is 'main' or different
}

variable "java_buildspec_file" {
  description = "The path to the buildspec file for your Java application within the GitHub repo."
  type        = string
  default     = "buildspec.yml" # Assumes buildspec.yml in the root, adjust if different
}

variable "github_oauth_token" {
  description = "GitHub OAuth token for CodePipeline/CodeBuild to access your private repository (if applicable)."
  type        = string
  sensitive   = true
}

# --- 1. S3 Bucket to hold the final JAR file ---
resource "aws_s3_bucket" "java_jar_bucket" {
  bucket        = var.java_app_bucket_name
  force_destroy = true # Use with extreme caution in production!
}

# --- 2. S3 Bucket for CodePipeline's internal artifacts ---
# CodePipeline needs a bucket to store source artifacts, build outputs, etc., during the pipeline execution.
resource "aws_s3_bucket" "codepipeline_artifact_store" {
  bucket        = var.codepipeline_artifact_bucket_name
  force_destroy = true # Use with extreme caution in production!
}

# Enable versioning, which is recommended for CodePipeline artifact stores
resource "aws_s3_bucket_versioning" "codepipeline_artifact_store_versioning" {
  bucket = aws_s3_bucket.codepipeline_artifact_store.id

  versioning_configuration {
    status = "Enabled"
  }
}

# --- 3. IAM Role for CodeBuild for Java ---
resource "aws_iam_role" "codebuild_java_role" {
  name = "codebuild-java-app-role"
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect = "Allow",
      Principal = {
        Service = "codebuild.amazonaws.com"
      },
      Action = "sts:AssumeRole"
    }]
  })
}

# NEW: IAM Policy for CodeBuild (correctly attached to CodeBuild role)
resource "aws_iam_role_policy" "codebuild_java_policy" {
  role = aws_iam_role.codebuild_java_role.name # Correctly reference the CodeBuild role

policy = jsonencode({
  Version = "2012-10-17",
  Statement = [
    {
      Effect = "Allow",
      Action = [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents",
        "logs:DescribeLogStreams"
      ],
      Resource = [
        "arn:aws:logs:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:log-group:/aws/codebuild/java-app-build",
        "arn:aws:logs:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:log-group:/aws/codebuild/java-app-build:*"
      ]
    },
    {
      Effect = "Allow",
      Action = [
        "s3:GetObject",
        "s3:PutObject",
        "s3:ListBucket",
        "s3:GetObjectVersion"
      ],
      Resource = [
        aws_s3_bucket.codepipeline_artifact_store.arn,
        "${aws_s3_bucket.codepipeline_artifact_store.arn}/*",
        aws_s3_bucket.java_jar_bucket.arn,
        "${aws_s3_bucket.java_jar_bucket.arn}/*"
      ]
    }
  ]
})
}

# Data source to get current AWS account ID for ARN construction
data "aws_caller_identity" "current" {}

# --- 4. CodeBuild Project for Java application ---
resource "aws_codebuild_project" "java_build" {
  name          = "java-app-build"
  description   = "Build Java app and prepare JAR for deployment"
  build_timeout = 20 # minutes
  service_role  = aws_iam_role.codebuild_java_role.arn

  artifacts {
    type = "CODEPIPELINE"
  }

  environment {
    # Reverting to BUILD_GENERAL1_SMALL and standard:5.0 (Java 17) which are generally compatible
    # If you *must* use Java 21 (standard:6.0), then change this to "BUILD_GENERAL1_LARGE"
    compute_type = "BUILD_GENERAL1_SMALL"
    image        = "aws/codebuild/amazonlinux2-x86_64-standard:5.0" # Java 17 (More likely to work with SMALL compute)
    type         = "LINUX_CONTAINER"
  }

  source {
    type            = "CODEPIPELINE"
    buildspec       = var.java_buildspec_file
    git_clone_depth = 1
  }
}

# --- 5. IAM Role and Policy for CodePipeline for Java ---
resource "aws_iam_role" "codepipeline_java_role" {
  name = "codepipeline-java-app-role" # Distinct name for Java CodePipeline role
  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Effect = "Allow",
      Principal = {
        Service = "codepipeline.amazonaws.com"
      },
      Action = "sts:AssumeRole"
    }]
  })
}

# Corrected Policy for CodePipeline (only CodePipeline specific permissions)
resource "aws_iam_role_policy" "codepipeline_java_policy" {
  role = aws_iam_role.codepipeline_java_role.name

  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [
      {
        Effect = "Allow",
        Action = [
          "s3:GetBucketVersioning",
          "s3:ListBucket",
          "s3:GetObject",
          "s3:PutObject",
          "s3:PutObjectAcl",
          "s3:DeleteObject"
        ],
        Resource = [
          aws_s3_bucket.codepipeline_artifact_store.arn,
          "${aws_s3_bucket.codepipeline_artifact_store.arn}/*"
        ]
      },
      {
        Effect = "Allow",
        Action = [
          "codebuild:BatchGetBuilds",
          "codebuild:StartBuild"
        ],
        Resource = aws_codebuild_project.java_build.arn
      },
      {
        Effect = "Allow",
        Action = [
          "s3:PutObject",
          "s3:GetObject",
          "s3:ListBucket",
          "s3:DeleteObject",
          "s3:PutObjectAcl"
        ],
        Resource = [
          aws_s3_bucket.java_jar_bucket.arn,
          "${aws_s3_bucket.java_jar_bucket.arn}/*"
        ]
      },
      {
        Effect = "Allow",
        Action = [
          "logs:CreateLogGroup",
          "logs:CreateLogStream",
          "logs:PutLogEvents"
        ],
        Resource = "arn:aws:logs:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:log-group:/aws/codepipeline/*"
      },
      {
        Effect = "Allow",
        Action = [
          "lambda:GetFunctionConfiguration",
          "lambda:UpdateFunctionCode",
          "lambda:UpdateFunctionConfiguration",
          "lambda:PublishVersion"
        ],
        Resource = [
          "arn:aws:lambda:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:function:SubmitScoreHandler",
          "arn:aws:lambda:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:function:LeaderboardHandler",
          "arn:aws:lambda:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:function:UserAuthHandler",
          "arn:aws:lambda:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:function:GameHandler"
        ]
      }
    ]
  })
}


data "aws_region" "current" {}


# --- 6. CodePipeline for Java App Deployment ---
resource "aws_codepipeline" "java_app_pipeline" {
  name     = "java-app-pipeline"
  role_arn = aws_iam_role.codepipeline_java_role.arn

  artifact_store {
    location = aws_s3_bucket.codepipeline_artifact_store.bucket
    type     = "S3"
  }

  # Source Stage: Pulls code from GitHub
  stage {
    name = "Source"
    action {
      name             = "SourceFromGitHub"
      category         = "Source"
      owner            = "ThirdParty"
      provider         = "GitHub"
      version          = "1"
      output_artifacts = ["source_output"]
      configuration = {
        Owner      = var.github_repo_owner
        Repo       = var.github_repo_name
        Branch     = var.github_repo_branch
        OAuthToken = var.github_oauth_token
      }
    }
  }

  # Build Stage: Builds the Java application using CodeBuild
  stage {
    name = "Build"
    action {
      name            = "BuildJavaApp"
      category        = "Build"
      owner           = "AWS"
      provider        = "CodeBuild"
      input_artifacts = ["source_output"]
      output_artifacts = ["java_build_output"] # Output artifact from CodeBuild
      version         = "1"
      configuration = {
        ProjectName = aws_codebuild_project.java_build.name
      }
    }
  }

  # Deploy Stage: Deploys the built JAR to the dedicated S3 bucket
  stage {
    name = "Deploy"
    action {
      name            = "DeployJarToS3"
      category        = "Deploy"
      owner           = "AWS"
      provider        = "S3"
      input_artifacts = ["java_build_output"] # Takes the JAR output from the Build stage
      version         = "1"
      configuration = {
        BucketName = aws_s3_bucket.java_jar_bucket.bucket # Deploy to the final JAR bucket
        Extract    = "false"
        ObjectKey  = "brainburst-backend-1.0-SNAPSHOT.jar"
      }
    }
    #     action {
    #       name             = "DeploySubmitScore"
    #       category         = "Deploy"
    #       owner            = "AWS"
    #       provider         = "Lambda"
    #       input_artifacts  = ["java_build_output"]
    #       version          = "1"
    #       configuration = {
    #         FunctionName = "SubmitScoreHandler"
    #       }
    #     }
    #
    #     action {
    #       name             = "DeployLeaderboard"
    #       category         = "Deploy"
    #       owner            = "AWS"
    #       provider         = "Lambda"
    #       input_artifacts  = ["java_build_output"]
    #       version          = "1"
    #       configuration = {
    #         FunctionName = "LeaderboardHandler"
    #       }
    #     }
    #
    #     action {
    #       name             = "DeployUserAuth"
    #       category         = "Deploy"
    #       owner            = "AWS"
    #       provider         = "Lambda"
    #       input_artifacts  = ["java_build_output"]
    #       version          = "1"
    #       configuration = {
    #         FunctionName = "UserAuthHandler"
    #       }
    #     }
    #
    #     action {
    #       name             = "DeployGame"
    #       category         = "Deploy"
    #       owner            = "AWS"
    #       provider         = "Lambda"
    #       input_artifacts  = ["java_build_output"]
    #       version          = "1"
    #       configuration = {
    #         FunctionName = "GameHandler"
    #       }
    #     }

  }
}