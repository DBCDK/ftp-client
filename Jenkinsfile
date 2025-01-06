#!groovy

def workerNode = "devel11"

pipeline {
    agent {label workerNode}

    tools {
        maven 'Maven 3'
    }

    environment {
        GITLAB_PRIVATE_TOKEN = credentials("metascrum-gitlab-api-token")
    }

    triggers {
        upstream(upstreamProjects: "Docker-payara6-bump-trigger",
                threshold: hudson.model.Result.SUCCESS)
    }

    options {
        timestamps()
        disableConcurrentBuilds()
    }

    stages {
        stage("clear workspace") {
            steps {
                deleteDir()
                checkout scm
            }
        }

        stage("verify") {
            steps {
                sh "mvn -D sourcepath=src/main/java verify pmd:pmd javadoc:aggregate"

                junit testResults: '**/target/*-reports/TEST-*.xml'

            }
        }


        stage("deploy to maven repository") {
            when {
                branch "master"
            }
            steps {
                sh """
                    mvn deploy -Dmaven.test.skip=true
                """
            }
        }
    }
}
