# # =============================
# # IAC 2: Backend CI/CD Pipeline
# # =============================
#
# # File: backend_pipeline.tf
# provider "aws" {
#   region = "us-east-1"
# }
#
# resource "aws_codepipeline" "backend_pipeline" {
#   name     = "brainburst-backend-pipeline"
#   role_arn = aws_iam_role.codepipeline_role.arn
#
#   artifact_store {
#     location = aws_s3_bucket.backend_artifacts.bucket
#     type     = "S3"
#   }
#
#   stage {
#     name = "Source"
#     action {
#       name             = "SourceAction"
#       category         = "Source"
#       owner            = "ThirdParty"
#       provider         = "GitHub"
#       version          = "1"
#       output_artifacts = ["source_output"]
#
#       configuration = {
#         Owner      = "AMS-JR"
#         Repo       = "brainburst-backend"
#         Branch     = "main"
#         OAuthToken = var.github_token
#       }
#     }
#   }
#
#   stage {
#     name = "Build"
#     action {
#       name             = "BuildAction"
#       category         = "Build"
#       owner            = "AWS"
#       provider         = "CodeBuild"
#       input_artifacts  = ["source_output"]
#       output_artifacts = ["build_output"]
#       version          = "1"
#
#       configuration = {
#         ProjectName = aws_codebuild_project.backend_build.name
#       }
#     }
#   }
#
#   stage {
#     name = "Deploy"
#     action {
#       name             = "DeploySubmitScore"
#       category         = "Deploy"
#       owner            = "AWS"
#       provider         = "Lambda"
#       input_artifacts  = ["build_output"]
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
#       input_artifacts  = ["build_output"]
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
#       input_artifacts  = ["build_output"]
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
#       input_artifacts  = ["build_output"]
#       version          = "1"
#       configuration = {
#         FunctionName = "GameHandler"
#       }
#     }
#
#   }
# }
#
# resource "aws_codebuild_project" "backend_build" {
#   name          = "brainburst-backend-build"
#   description   = "Builds the Java Lambda JAR"
#   build_timeout = 5
#   service_role  = aws_iam_role.codebuild_role.arn
#
#   artifacts {
#     type = "CODEPIPELINE"
#   }
#
#   environment {
#     compute_type                = "BUILD_GENERAL1_SMALL"
#     image                       = "aws/codebuild/amazoncorretto:17"
#     type                        = "LINUX_CONTAINER"
#   }
#
#   source {
#     type      = "CODEPIPELINE"
#     buildspec = "../buildspec.yml"
#   }
# }
#
# resource "aws_s3_bucket" "backend_artifacts" {
#   bucket = "brainburst-backend-artifacts"
# }
#
# resource "aws_iam_role" "codepipeline_role" {
#   name = "brainburst-backend-codepipeline-role"
#
#   assume_role_policy = jsonencode({
#     Version = "2012-10-17"
#     Statement = [
#       {
#         Effect = "Allow"
#         Principal = {
#           Service = "codepipeline.amazonaws.com"
#         }
#         Action = "sts:AssumeRole"
#       }
#     ]
#   })
# }
#
# resource "aws_iam_role" "codebuild_role" {
#   name = "brainburst-backend-codebuild-role"
#
#   assume_role_policy = jsonencode({
#     Version = "2012-10-17"
#     Statement = [
#       {
#         Effect = "Allow"
#         Principal = {
#           Service = "codebuild.amazonaws.com"
#         }
#         Action = "sts:AssumeRole"
#       }
#     ]
#   })
# }
#
# variable "github_token" {
#   description = "GitHub OAuth token for CodePipeline"
#   type        = string
#   sensitive   = true
# }
