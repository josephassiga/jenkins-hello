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
def APP_GIT_COMMIT = ''
// Root folder of configuration files.
def BUILD_FOLDER='infra/build/'
def DEPLOY_FOLDER='infra/deploy/'

/***************************************/
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
            choice(name: 'DEPLOYMENT_TYPE', choices: ['','BUILD', 'DEPLOY'], description: 'The name of the type of deployment to process, can be either Build or Deploy.')
            string(name: 'JENKINS_ACCOUNT', defaultValue: 'safranae-portfolioopmatiseco-matis-me-int-jenkins-z15', description: 'Name of the Openshift Jenkins Account used to deploy resources on the cluster.')
            string(name: 'SOURCES_URL', defaultValue: 'https://github.com/josephassiga/jenkins-hello.git', description: 'Gitlab repository source of the application.')

        }

        // List of stages used on the pipelines.
        stages
        {
            stage('Checkout.')
            {

                when {
                    // Only for INT environment and BUILD Deployment type.
                         expression { params.ENVIRONMENT == 'INT'  &&  params.DEPLOYMENT_TYPE == 'BUILD' }
                }
                steps
                {
                    script
                    {
                            if ( params.DEPLOYMENT_TYPE.isEmpty() )
                            {
                                // Use SUCCESS FAILURE or ABORTED
                                currentBuild.result = 'FAILURE'
                                throw new Exception("The Parameters DEPLOYMENT_TYPE is empty, the value must be either BUILD or DEPLOY, Please set DEPLOYMENT_TYPE parameters.")
                            }
                    }

                    checkout([$class: 'GitSCM', 
                            branches: [[name: "*/${env.BRANCH}"]], 
                            userRemoteConfigs:[[url: "${env.SOURCES_URL}"]]
                    ])
                    
                    script 
                    {
                        // Get a Hash commit to pass to the tag image
                        APP_GIT_COMMIT =  sh (script: "git log -n 1 --pretty=format:'%h'", returnStdout: true).trim()
                        echo "**************************************************"
                        echo " The tag used for image is :  ${APP_GIT_COMMIT}   "
                        echo "**************************************************"
                    } // script
                } // steps
            } // stage
            stage("Create Template for Build.")
            {
                when {
                    // Only for INT environment and BUILD Deployment type.
                    expression { 
                        params.ENVIRONMENT == 'INT' 
                        params.DEPLOYMENT_TYPE == 'BUILD' 
                     }
                }
                steps
                {
                    script
                    {
                        openshift.withCluster( "${params.CLUSTER_NAME}" )
                        {
                            openshift.withProject( "${params.MATIS_ECO_NAMESPACE}" )
                            {
                                 openshift.withCredentials( "${params.JENKINS_ACCOUNT}" )
                                 {
                                    // Create the template
                                    def resources = openshift.apply( readFile("${BUILD_FOLDER}${MATIS_BUILD_TEMPLATE_NAME}-template.yaml" ))

                                    echo "Template : ${MATIS_BUILD_TEMPLATE_NAME} resources : ${resources} created sucessfully."
                                 } // openshift.withCredentials
                            } // openshift.withProject
                        } // openshift.withCluster
                    } // script
                } // steps
            } // stage
            stage("Create BuildConfigs.") 
            {   
                when {
                    // Only for INT environment and BUILD Deployment type.
                    expression { params.ENVIRONMENT == 'INT'  &&  params.DEPLOYMENT_TYPE == 'BUILD' }
                }
                steps
                {
                    script
                    {
                        openshift.withCluster("${params.CLUSTER_NAME}")
                        {
                            openshift.withProject( "${params.MATIS_ECO_NAMESPACE}" )
                            {    
                                def  MATIS_ECO_PROPERTIES_FILE= "${env.WORKSPACE}/${BUILD_FOLDER}${MATIS_BUILD_TEMPLATE_NAME}-${params.ENVIRONMENT.toLowerCase()}.properties" 
                                script 
                                {
                                     // Script to pass for the commit used to build the image.
                                     sh """
                                      
                                      sed -i -e "s/MATIS_ECO_APP_COMMIT_TAG='latest'/MATIS_ECO_APP_COMMIT_TAG=${APP_GIT_COMMIT}/g" "${MATIS_ECO_PROPERTIES_FILE}"

                                      cat "${MATIS_ECO_PROPERTIES_FILE}"

                                    """  
                                } // script
                                
                                openshift.withCredentials( "${params.JENKINS_ACCOUNT}" )
                                {
                                    def buildConfigResources = openshift.apply(
                                            openshift.process("${MATIS_BUILD_TEMPLATE_NAME}",
                                            "--param-file=${BUILD_FOLDER}${MATIS_BUILD_TEMPLATE_NAME}-${params.ENVIRONMENT.toLowerCase()}.properties"
                                        )
                                    )
                                    echo "Build Config  ${buildConfigResources} created sucessfully."
                                } // openshift.withCredentials
                            }
                        }   // openshift.withCluster
                    } // script  
                } // steps
            } // stage
            stage("Trigger Build.") 
            {
                when {
                    // Only for INT environment and BUILD Deployment type.
                    expression { params.ENVIRONMENT == 'INT'  &&  params.DEPLOYMENT_TYPE == 'BUILD' }
                }
                steps
                {
                    script
                    {
                        openshift.withCluster("${params.CLUSTER_NAME}") 
                        {
                            openshift.withProject( "${params.MATIS_ECO_NAMESPACE}" )
                            {
                                openshift.withCredentials( "${params.JENKINS_ACCOUNT}" )
                                {          
                                    echo "The Buildconfig to trigger : ${MATIS_BUILD_TEMPLATE_NAME} ."
                                        
                                    def buildCondig = openshift.selector("buildconfig","${MATIS_BUILD_TEMPLATE_NAME}")

                                    echo "Start Buildconfig ${MATIS_BUILD_TEMPLATE_NAME} ."

                                    def builds =  buildCondig.startBuild() // Start a new build.
                                
                                    builds.untilEach(1) { // We want a minimum of 1 build

                                        // Unlike watch(), untilEach binds 'it' to a Selector for a single object.
                                        // Thus, untilEach will only terminate when all selected objects satisfy this
                                        // the condition established in the closure body (or until the timeout(10)
                                        // interrupts the operation).

                                        return it.object().status.phase == "Complete"
                                    }
                                    
                                    echo "Build Config  ${buildCondig} completed sucessfully." 
                                }// openshift.withCredentials
                            }// openshift.withProject
                        }// openshift.withCluster() 
                    }// script  
                } // steps
            } // stage
            stage("Create Template for Deployment.")
            {
                
                when {
                    // Only for INT/AP/OP environment and BUILD Deployment type.
                    expression { params.DEPLOYMENT_TYPE == 'DEPLOY' }
                }
                steps 
                {
                    script
                    {
                        openshift.withCluster( "${params.CLUSTER_NAME}" )
                        {
                            openshift.withProject( "${params.MATIS_ECO_NAMESPACE}" )
                            {
                                openshift.withCredentials( "${params.JENKINS_ACCOUNT}" )
                                {
                                    // Create the template
                                    def resources = openshift.apply( readFile( "${DEPLOY_FOLDER}${MATIS_DEPLOY_TEMPLATE_NAME}-template.yaml" ))

                                    echo "Template : ${MATIS_DEPLOY_TEMPLATE_NAME} resources : ${resources} created sucessfully." 
                                }// openshift.withCredentials
                            } // openshift.withProject
                        } // openshift.withCluster
                    } // script
                }// steps
            } // stage
            stage("Deploy resources for INT/AP/OP environments.") 
            {               
                when {
                    // Only for INT/AP/OP environment and BUILD Deployment type.
                    expression { params.DEPLOYMENT_TYPE == 'DEPLOY' }
                }
                steps
                { 
                    script
                    {
                        openshift.withCluster( "${params.CLUSTER_NAME}" )
                        {
                            openshift.withProject( "${params.MATIS_ECO_NAMESPACE}" )
                            {
                                    openshift.withCredentials( "${params.JENKINS_ACCOUNT}" )
                                    {
                                        def deploymentName = "experience-api-gateway-${params.ENVIRONMENT.toLowerCase()}-matis-eco"
                                        
                                        def resources = openshift.apply(
                                                openshift.process("${MATIS_DEPLOY_TEMPLATE_NAME}",
                                                    "--param-file=${DEPLOY_FOLDER}${MATIS_DEPLOY_TEMPLATE_NAME}-${params.ENVIRONMENT.toLowerCase()}.properties"
                                                )
                                        )
                                        echo "Waiting ${deploymentName} deployment to complete ..."
                                        
                                        def deploy = openshift.selector("deployment", "${deploymentName}")

                                        deploy.untilEach(1){
                                            def rcMap = it.object()
                                            return (rcMap.status.replicas.equals(rcMap.status.readyReplicas))
                                        }
                                        
                                        echo "Deployment ${deploymentName} creation is successfully"                                        
                                    }// openshift.withCredentials(
                            } // openshift.withProject
                        } // openshift.withCluster()
                    } // script
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