# Configure the AWS provider
provider "aws" {
  region = "us-east-1" # Ensure this matches your desired region
}

# --- Task 1: Create a Simple AWS Lambda Function ---

# 1.1 Create the Lambda Function Code Locally
# This local_file resource writes your JavaScript code to a file
# and the archive_file data source zips it up for Lambda deployment.
resource "local_file" "lambda_code_file" {
  content  = <<EOF
export const handler = async (event) => {
  const response = {
    statusCode: 200,
    body: JSON.stringify({
      message: 'Current date and time',
      dateTime: new Date().toISOString()
    }),
  };
  return response;
};
EOF
  filename = "lambda_function.js"
}

# 1.2 Zip the Lambda function code
data "archive_file" "lambda_zip" {
  type        = "zip"
  source_file = local_file.lambda_code_file.filename
  output_path = "lambda_function.zip"
}

# 1.3 Create an IAM Role for the Lambda function
resource "aws_iam_role" "lambda_exec_role" {
  name = "lambda-date-time-executor-role"

  assume_role_policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Action = "sts:AssumeRole",
      Effect = "Allow",
      Principal = {
        Service = "lambda.amazonaws.com"
      }
    }]
  })
}

# 1.4 Attach a policy to the Lambda role for CloudWatch Logs permissions
resource "aws_iam_role_policy" "lambda_logging_policy" {
  name = "lambda-date-time-logging-policy"
  role = aws_iam_role.lambda_exec_role.id

  policy = jsonencode({
    Version = "2012-10-17",
    Statement = [{
      Action = [
        "logs:CreateLogGroup",
        "logs:CreateLogStream",
        "logs:PutLogEvents"
      ],
      Effect   = "Allow",
      Resource = "arn:aws:logs:*:*:*" # Allows logging to any log group/stream
    }]
  })
}

# Data source to package your Java Lambda JAR file
# Replace './path/to/your/submit-score-handler.jar' with the actual path to your compiled JAR file.
# Typically, this would be found in your Java project's 'target' directory after a Maven/Gradle build.
# IAM role reused for all Lambdas
data "archive_file" "submit_score_lambda_zip" {
  type        = "zip"
  source_file = "../../target/brainburst-backend-1.0-SNAPSHOT.jar" # Adjust this path to your JAR file
  output_path = "brainburst-backend-1.0-SNAPSHOT.zip"
}

# SubmitScoreHandler7
resource "aws_lambda_function" "submit_score_handler" {
  function_name    = "SubmitScoreHandler7"
  handler          = "com.brainburst.score.SubmitScoreHandler::handleRequest"
  runtime          = "java17"
  role             = aws_iam_role.lambda_exec_role.arn
  filename         = data.archive_file.submit_score_lambda_zip.output_path
  source_code_hash = data.archive_file.submit_score_lambda_zip.output_base64sha256
  memory_size      = 512
  timeout          = 15
  architectures    = ["x86_64"]

  environment {
    variables = {
      SCORES_TABLE     = "Scores7"
      SES_SENDER_EMAIL = "asj.sarjo@gmail.com"
    }
  }

  tags = {
    Project = "BrainBurst"
    Name    = "SubmitScoreHandler7"
  }
}

# GameHandler7
resource "aws_lambda_function" "game_handler" {
  function_name    = "GameHandler7"
  handler          = "com.brainburst.game.GameHandler::handleRequest"
  runtime          = "java17"
  role             = aws_iam_role.lambda_exec_role.arn
  filename         = data.archive_file.submit_score_lambda_zip.output_path
  source_code_hash = data.archive_file.submit_score_lambda_zip.output_base64sha256
  memory_size      = 512
  timeout          = 15
  architectures    = ["x86_64"]

  tags = {
    Project = "BrainBurst"
    Name    = "GameHandler7"
  }
}

# LeaderboardHandler7
resource "aws_lambda_function" "leaderboard_handler" {
  function_name    = "LeaderboardHandler7"
  handler          = "com.brainburst.leaderboard.LeaderboardHandler::handleRequest"
  runtime          = "java17"
  role             = aws_iam_role.lambda_exec_role.arn
  filename         = data.archive_file.submit_score_lambda_zip.output_path
  source_code_hash = data.archive_file.submit_score_lambda_zip.output_base64sha256
  memory_size      = 512
  timeout          = 15
  architectures    = ["x86_64"]

  environment {
    variables = {
      SCORES_TABLE     = "Scores7"
    }
  }

  tags = {
    Project = "BrainBurst"
    Name    = "LeaderboardHandler7"
  }
}

# UserAuthHandler7
resource "aws_lambda_function" "user_auth_handler" {
  function_name    = "UserAuthHandler7"
  handler          = "com.brainburst.auth.UserAuthHandler::handleRequest"
  runtime          = "java17"
  role             = aws_iam_role.lambda_exec_role.arn
  filename         = data.archive_file.submit_score_lambda_zip.output_path
  source_code_hash = data.archive_file.submit_score_lambda_zip.output_base64sha256
  memory_size      = 512
  timeout          = 15
  architectures    = ["x86_64"]

  environment {
    variables = {
      USERS_TABLE     = "Users7"
    }
  }

  tags = {
    Project = "BrainBurst"
    Name    = "UserAuthHandler7"
  }
}


# --- Task 3 (moved up): Create Amazon Cognito User Pool and Authorizer ---
# This section is moved up so the authorizer can be referenced by the API Gateway method.

# 3.1 Create a Cognito User Pool
resource "aws_cognito_user_pool" "web_app_user_pool" {
  name = "MyWebAppUserPool"

  # Configure sign-in options as 'email'
  username_attributes = ["email"]

  # Set password policy
  password_policy {
    minimum_length    = 8
    require_lowercase = false
    require_numbers   = false
    require_symbols   = false
    require_uppercase = false
  }

  # Configure message delivery for email verification
  email_configuration {
    email_sending_account = "COGNITO_DEFAULT" # For testing, limited emails per day
  }
  lambda_config {
    post_confirmation = aws_lambda_function.user_auth_handler.arn
  }

  # Set required attributes for sign-up
  schema {
    name                = "email"
    attribute_data_type = "String"
    mutable             = true
    required            = true
  }

  # Enable email verification
  auto_verified_attributes = ["email"]
}

# **NEW ADDITION: Cognito User Pool Domain**
# This is crucial for the Hosted UI to work.
resource "aws_cognito_user_pool_domain" "web_app_user_pool_domain" {
  domain       = "my-web-app-auth-${lower(random_string.suffix.result)}" # Use a unique, random suffix
  user_pool_id = aws_cognito_user_pool.web_app_user_pool.id
}

# Helper to generate a unique suffix for the domain
resource "random_string" "suffix" {
  length  = 8
  special = false
  upper   = false
  numeric = true
}

# 3.2 Create a Cognito User Pool App Client
resource "aws_cognito_user_pool_client" "web_client_app" {
  name         = "My Web Client"
  user_pool_id = aws_cognito_user_pool.web_app_user_pool.id

  # Important: Set allowed OAuth flows as "implicit" as requested
  allowed_oauth_flows            = ["implicit"]
  allowed_oauth_flows_user_pool_client = true # Must be true for OAuth flows
  allowed_oauth_scopes           = ["openid", "email", "profile"] # Common scopes for user info

  # Set the return URL
  callback_urls = ["https://localhost:3000"]
  logout_urls   = ["https://localhost:3000"] # Often the same as callback

  # SPAs are public clients, no client secret required
  explicit_auth_flows = ["ALLOW_ADMIN_USER_PASSWORD_AUTH", "ALLOW_CUSTOM_AUTH", "ALLOW_USER_SRP_AUTH", "ALLOW_REFRESH_TOKEN_AUTH"] # Required for implicit grant with Hosted UI
  supported_identity_providers = ["COGNITO"]
}


# 3.3 Create an API Gateway Authorizer using the Cognito User Pool
resource "aws_api_gateway_authorizer" "cognito_authorizer" {
  name                     = "CognitoAuthorizer"
  rest_api_id              = aws_api_gateway_rest_api.brainburst_backend.id
  type                     = "COGNITO_USER_POOLS"
  provider_arns            = [aws_cognito_user_pool.web_app_user_pool.arn]
  identity_source          = "method.request.header.Authorization"
  authorizer_result_ttl_in_seconds = 300

  depends_on = [
    aws_cognito_user_pool.web_app_user_pool,
    aws_cognito_user_pool_client.web_client_app,
  ]
}

# --- Task 2: Set Up API Gateway (now with Cognito Authorizer directly) ---

# REST API
resource "aws_api_gateway_rest_api" "brainburst_backend" {
  name = "brainburst-backend"
  description = "API to brainburst-backend"

  tags = {
    Project = "brainburst-backend"
  }
}

# Base resource "/"
resource "aws_api_gateway_resource" "score" {
  rest_api_id = aws_api_gateway_rest_api.brainburst_backend.id
  parent_id   = aws_api_gateway_rest_api.brainburst_backend.root_resource_id
  path_part   = "score"
}

resource "aws_api_gateway_resource" "game" {
  rest_api_id = aws_api_gateway_rest_api.brainburst_backend.id
  parent_id   = aws_api_gateway_rest_api.brainburst_backend.root_resource_id
  path_part   = "game"
}

resource "aws_api_gateway_resource" "leaderboard" {
  rest_api_id = aws_api_gateway_rest_api.brainburst_backend.id
  parent_id   = aws_api_gateway_rest_api.brainburst_backend.root_resource_id
  path_part   = "leaderboard"
}

# POST /score
resource "aws_api_gateway_method" "post_score" {
  rest_api_id   = aws_api_gateway_rest_api.brainburst_backend.id
  resource_id   = aws_api_gateway_resource.score.id
  http_method   = "POST"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "post_score" {
  rest_api_id             = aws_api_gateway_rest_api.brainburst_backend.id
  resource_id             = aws_api_gateway_resource.score.id
  http_method             = aws_api_gateway_method.post_score.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.submit_score_handler.invoke_arn
}

# GET /game
resource "aws_api_gateway_method" "get_game" {
  rest_api_id   = aws_api_gateway_rest_api.brainburst_backend.id
  resource_id   = aws_api_gateway_resource.game.id
  http_method   = "GET"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "get_game" {
  rest_api_id             = aws_api_gateway_rest_api.brainburst_backend.id
  resource_id             = aws_api_gateway_resource.game.id
  http_method             = aws_api_gateway_method.get_game.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.game_handler.invoke_arn
}

# GET /leaderboard
resource "aws_api_gateway_method" "get_leaderboard" {
  rest_api_id   = aws_api_gateway_rest_api.brainburst_backend.id
  resource_id   = aws_api_gateway_resource.leaderboard.id
  http_method   = "GET"
  authorization = "NONE"
}

resource "aws_api_gateway_integration" "get_leaderboard" {
  rest_api_id             = aws_api_gateway_rest_api.brainburst_backend.id
  resource_id             = aws_api_gateway_resource.leaderboard.id
  http_method             = aws_api_gateway_method.get_leaderboard.http_method
  integration_http_method = "POST"
  type                    = "AWS_PROXY"
  uri                     = aws_lambda_function.leaderboard_handler.invoke_arn
}

# # 2.5 Grant API Gateway permission to invoke the Lambda function
# resource "aws_lambda_permission" "apigw_lambda_permission" {
#   statement_id  = "AllowAPIGatewayInvokeLambda"
#   action        = "lambda:InvokeFunction"
#   # Corrected the reference from "SubmitScoreHandler" to "submit_score_handler" (lowercase 's')
#   function_name = aws_lambda_function.submit_score_handler.function_name
#   principal     = "apigateway.amazonaws.com"
#
#   # The /*/* allows API Gateway to invoke the Lambda from any stage and method
#   source_arn = "${aws_api_gateway_rest_api.date_time_api.execution_arn}/*/*"
# }
resource "aws_lambda_permission" "allow_cognito_post_confirmation" {
  statement_id  = "AllowExecutionFromCognito"
  action        = "lambda:InvokeFunction"
  function_name = aws_lambda_function.user_auth_handler.function_name
  principal     = "cognito-idp.amazonaws.com"
  source_arn    = aws_cognito_user_pool.web_app_user_pool.arn
}


# 2.6 Deploy the API
resource "aws_api_gateway_deployment" "backend_deploy" {
  rest_api_id = aws_api_gateway_rest_api.brainburst_backend.id

  triggers = {
    redeploy = sha1(jsonencode([
      aws_api_gateway_method.post_score.id,
      aws_api_gateway_method.get_game.id,
      aws_api_gateway_method.get_leaderboard.id
    ]))
  }

  depends_on = [
    aws_api_gateway_integration.post_score,
    aws_api_gateway_integration.get_game,
    aws_api_gateway_integration.get_leaderboard,
  ]
}

resource "aws_api_gateway_stage" "prod" {
  stage_name    = "prod"
  rest_api_id   = aws_api_gateway_rest_api.brainburst_backend.id
  deployment_id = aws_api_gateway_deployment.backend_deploy.id
}


# Output the final invoke URL for testing
output "api_gateway_invoke_url" {
  value = "${aws_api_gateway_stage.prod.invoke_url}"
}

output "cognito_user_pool_id" {
  description = "The ID of the created Cognito User Pool"
  value       = aws_cognito_user_pool.web_app_user_pool.id
}

output "cognito_app_client_id" {
  description = "The ID of the created Cognito User Pool App Client"
  value       = aws_cognito_user_pool_client.web_client_app.id
}

output "cognito_user_pool_domain" {
  description = "The full domain URL for the Cognito Hosted UI"
  value       = aws_cognito_user_pool_domain.web_app_user_pool_domain.domain
}

# S3 Bucket
resource "aws_s3_bucket" "jar_bucket" {
  bucket        = "backend-jar-bucket-20250615" # Hardcoded S3 bucket name
  force_destroy = true
}

# DynamoDB
# DynamoDB Table for Users
resource "aws_dynamodb_table" "users_table" {
  name         = "Users7"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "userId" # Using 'email' as the Partition Key as per your schema
  range_key    = "email"  # Using 'scoreId' as the Sort Key for uniqueness per user

  attribute {
    name = "userId"
    type = "S" # String type for email
  }

  attribute {
    name = "email"
    type = "S" # String type for UUID (scoreId)
  }
}

# DynamoDB Table for Scores
resource "aws_dynamodb_table" "scores_table" {
  name         = "Scores7"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "scoreId" # Using 'username' as the Partition Key
  range_key    = "user"    # Using 'scoreId' as the Sort Key for uniqueness per user

  attribute {
    name = "scoreId"
    type = "S" # String type for username
  }

  attribute {
    name = "user"
    type = "S" # String type for UUID (scoreId)
  }
}
