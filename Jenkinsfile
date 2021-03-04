/**********************************************************************************************************************/
/* @author: Mejri Issam, Joseph ASSIGA.                                                                               */
/* @data: 10/02/2021.                                                                                                 */
/* @description: This pipeline is used to deploy resources on an Openshift cluster 4.6.                               */
/* @links :                                                                                                           */
/*      1. Documentation for Openshift Jenkins Plugins used : https://github.com/openshift/jenkins-client-plugin.     */
/*      2. Documentation for Jenkins Pipeline:                                                                        */
/*          - Jenkins Pipeline syntax : https://www.jenkins.io/doc/book/pipeline/syntax/.                             */
/*          - Checkout syntax : https://www.jenkins.io/doc/pipeline/steps/workflow-scm-step/                          */
/**********************************************************************************************************************/


// Content the git commit that will be used to tag the image.
def APP_IMAGE_TAG = ''
// Root folder of configuration files.
def BUILD_FOLDER='infra/build/'
def DEPLOY_FOLDER='infra/deploy/'

/*************************************++/
 /* VARIABLES TO CHANGE WHEN COPIED    */
 /**************************************/
def MATIS_BUILD_TEMPLATE_NAME="matis-eco-experience-api-gateway-build"
def MATIS_DEPLOY_TEMPLATE_NAME="matis-eco-experience-api-gateway-deploy"

pipeline {
        // We will use any agent available.
        agent any

        // set a timeout of 30 minutes for this pipeline. 
        options
        {
            timeout(time: 30, unit: 'MINUTES')
        }

        // List of parameters required to configure before the launch of the pipeline.
        parameters{
            string(name: 'BRANCH', defaultValue: 'main', description: 'Name of the branch to use.')
            string(name: 'CLUSTER_NAME', defaultValue: 'non-production', description: 'Name of the cluster where ressources will be deployed.')
            choice(name: 'ENVIRONMENT', choices: ['INT', 'AP', 'OP'], description: 'The name of the environement where we want to deploy/build resources.')
            string(name: 'MATIS_ECO_NAMESPACE', defaultValue: 'safranae-portfolioopmatiseco-matis-me-int', description: 'Name of the Openshift namespace to use.')
            string(name: 'JENKINS_ACCOUNT', defaultValue: 'safranae-portfolioopmatiseco-matis-me-int-jenkins-z15', description: 'Name of the Openshift Jenkins Account used to deploy resources on the cluster.')
            string(name: 'SOURCES_URL', defaultValue: 'https://github.com/josephassiga/jenkins-hello.git', description: 'Gitlab repository source of the application.')

        }

        // List of stages used on the pipelines.
        stages
        {
            stage('Checkout.')
            {
                steps
                {
                        
                    checkout([$class: 'GitSCM', 
                            branches: [[name: "*/${env.BRANCH}"]], 
                            userRemoteConfigs:[[url: "${env.SOURCES_URL}"]]
                    ])
                    
                    //script 
                    //{
                                // Get a Hash commit to pass to the tag image
                                APP_IMAGE_TAG = "${params.ENVIRONMENT == 'AP'} ? 'PromoteToAP' : ${params.ENVIRONMENT == 'OP'} ? 'PromoteToOP' : 'PromoteToDEV'"
                                echo "**************************************************"
                                echo "The commit HASH is ${APP_IMAGE_TAG}"
                                echo "**************************************************"
                   // } // script
                } // steps
            } // stage
        } // stages
        post 
        {
            always 
            {
                deleteDir() // Delete the current workspace.
                dir("${env.WORKSPACE}@script") 
                { 
                    deleteDir() // Delete the script workspace, this allow us to update the pipeline script without deleting the builconfig.
                } // dir
            } // always
        } // post 
} // pipeline