#!groovy
@Library(['jenkins-shared-pipelines@poc-testcontainers']) _

appData=[
  binaryPath : "gen-poc-localstack/target",
  jdkVersion: "17",
  mavenTestGoals: "test"
]

if (env.BRANCH_NAME == "master") {
    echo "Production releases should go through release pipeline"
} else if (env.BRANCH_NAME == "integration") {
    promote(appData)
} else {
    dev_mavenTestContainers(appData)
}
