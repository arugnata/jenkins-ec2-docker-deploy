pipeline {
    agent any

    environment {
        SSH_KEY64 = credentials('SSH_KEY64')
        DOCKER_CREDS = credentials('dockerhubid-pipeline')
        
        IMAGE_NAME = "arugnata/nginx-site"
        IMAGE_TAG = "latest"
        CONTAINER_NAME = "nginx-site"
        EC2_USER = "ubuntu"
    }

    parameters {
        string(
            name: 'SERVER_IP',
            defaultValue: '44.201.5.161',
            description: 'Target EC2 Server IP'
        )
    }

    stages {
        stage('Build Docker Image') {
            steps {
                script {
                    sh "docker build -t ${IMAGE_NAME}:${IMAGE_TAG} ."
                }
            }
        }

        stage('Login & Push to Docker Hub') {
            steps {
                script {
                    sh "echo ${DOCKER_CREDS_PSW} | docker login -u ${DOCKER_CREDS_USR} --password-stdin"
                    sh "docker push ${IMAGE_NAME}:${IMAGE_TAG}"
                }
            }
        }

        stage('Deploy to EC2') {
            steps {
                script {
                    sh "echo ${SSH_KEY64} | base64 -d > ec2_key.pem"
                    sh "chmod 400 ec2_key.pem"

                    def sshCmd = "ssh -o StrictHostKeyChecking=no -i ec2_key.pem ${EC2_USER}@${params.SERVER_IP}"

                    echo "Deploying to EC2 as ${EC2_USER}..."
                    
                    sh """
                        ${sshCmd} "
                            docker login -u ${DOCKER_CREDS_USR} -p ${DOCKER_CREDS_PSW} &&
                            docker pull ${IMAGE_NAME}:${IMAGE_TAG} &&
                            docker ps -aq -f name=${CONTAINER_NAME} | xargs -r docker rm -f &&
                            docker run -d --name ${CONTAINER_NAME} -p 80:80 ${IMAGE_NAME}:${IMAGE_TAG}
                        "
                    """
                }
            }
        }
    }

    post {
        always {
            sh "rm -f ec2_key.pem"
            sh "docker logout"
        }
    }
}