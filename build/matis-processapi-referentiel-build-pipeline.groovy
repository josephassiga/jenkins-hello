/*
  @author: Mejri Issam, Joseph ASSIGA
  @data: 10/02/2021
  @description: This pipeline is used to deploy resources on an Openshift cluster 4.6
  @link 
    1. Documentation for Openshif Jenkins Plugins used : https://github.com/openshift/jenkins-client-plugin.
    2. Documentation for Jenkins Pipeline: 
        - Jenkins Pipeline syntax : https://www.jenkins.io/doc/book/pipeline/syntax/.
        - Checkout syntax : https://www.jenkins.io/doc/pipeline/steps/workflow-scm-step/
*/


// Content the git commit that will be used to tag the image.
def MATIS_IMAGE_TAG = ''
// Root folder of configuration files.
def BUILD_FOLDER='infra/build/'
// Safran Quay repository name.
def SAFRAN_QUAY_REGISTRY='quay.apps.prd.dc1.cloud.safran/redhat'
// Matis Eco Microservice iamge.
def MATIS_ECO_COMPONENT_IMAGE_NAME='process-api-referentiel-web-api'


/*************************************++/
 /* VARIABLES TO CHANGE WHEN COPIED    */
 /**************************************/
def MATIS_BUILD_TEMPLATE_NAME="matis-eco-process-api-referentiel-web-api-build" // To change with the name of the microservice.

pipeline {
        agent
        {
                node
                {
                       
                        label 'master' // Select the master node to run the build.
                }
        }
        options
        {
            timeout(time: 30, unit: 'MINUTES') // set a timeout of 30 minutes for this pipeline.
        }
        parameters{
            string(name: 'BRANCH', defaultValue: 'master', description: 'Name of the branch to use.') // Change this value if you want another branch.
            string(name: 'CLUSTER_NAME', defaultValue: 'non-production', description: 'Name of the cluster where ressources will be deployed.')
            choice(name: 'ENVIRONMENT', choices: ['INT', 'AP', 'OP'], description: 'The name of the environement where we want to deploy/build resources.')
            string(name: 'MATIS_ECO_NAMESPACE', defaultValue: 'safranae-portfolioopmatiseco-matis-me-int', description: 'Name of the matis namespace to use.')
            string(name: 'JENKINS_ACCOUNT', defaultValue: 'safranae-portfolioopmatiseco-matis-me-int-jenkins-z15', description: 'Name of the Openshift Jenkins Account used to deploy resources on the cluster.')
            string(name: 'SOURCES_URL', defaultValue: 'https://git.cloud.safran/safranae/portfolioopmatiseco/matis-me-int/experience/matiseco.experienceapi.gateway.git', description: 'Gitlab repository source of the application.')

        }
        stages
        {
            stage('checkout')
            {
                steps
                {
                        
                        checkout([$class: 'GitSCM', 
                                branches: [[name: "*/${env.BRANCH}"]], 
                                userRemoteConfigs:[[credentialsId:  'gitlab', url: "${env.SOURCES_URL}"]]
                        ])
                        
                        script {
                                    // Get a Hash commit to pass to the tag image
                                    MATIS_IMAGE_TAG = sh (script: "git log -n 1 --pretty=format:'%h'", returnStdout: true)
                                    echo "**************************************************"
                                    echo "The commit HASH is ${MATIS_IMAGE_TAG}"
                                    echo "**************************************************"
                        }
                } // steps
            } // stage
            stage("Create Template.")
            {
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

                                    // Declare a list of all images with the image Stream name.
                                    def  matisTemplates = [
                                        "${MATIS_BUILD_TEMPLATE_NAME}"
                                    ]

                                    echo "Number of templates to create : ${matisTemplates.size()} ."

                                    matisTemplates.each { currentTemplate ->
                                        // Create the template
                                        def resources = openshift.apply( readFile( "${BUILD_FOLDER}${currentTemplate}-template.yaml" ))

                                        echo "Template : ${currentTemplate} resources : ${resources} created sucessfully."
                                    }
                                        
                                 }
                            } // openshift.withProject
                        } // openshift.withCluster()
                    } // script
                } // steps
            } // stage
            stage("Create BuildConfigs.") 
            {

                when{
                    environment name: 'ENVIRONMENT', value: 'INT'
                }
                    // Declare PATH to the propreties file
                  def  MATIS_ECO_PROPERTIES_FILE= "${env.WORKSPACE}/${BUILD_FOLDER}${params.MATIS_BUILD_TEMPLATE_NAME}-${params.ENVIRONMENT.toLowerCase()}.properties"
                steps
                {
                    script
                    {
                        openshift.withCluster("${params.CLUSTER_NAME}")
                        {
                            openshift.withProject( "${params.MATIS_ECO_NAMESPACE}" )
                            {    
                                script {
                                    // Script to pass the new tag to the image (Change tag in the propreties file)
                                     sh """
                                      sed -i -e "s/MATIS_ECO_IMAGE_TAG='latest'/MATIS_ECO_IMAGE_TAG=${MATIS_IMAGE_TAG}/g" "${MATIS_ECO_PROPERTIES_FILE}.properties"
                                     
                                      cat "${MATIS_ECO_PROPERTIES_FILE}"
                                    """
                                }
                                
                                openshift.withCredentials( "${params.JENKINS_ACCOUNT}" )
                                {
                                    def buildConfigResources = openshift.apply(
                                            openshift.process("${MATIS_BUILD_TEMPLATE_NAME}",
                                            "--param-file=${BUILD_FOLDER}${params.MATIS_BUILD_TEMPLATE_NAME}-${params.ENVIRONMENT.toLowerCase()}.properties"
                                        )
                                    )
                                    echo "Build Config  ${buildConfigResources} created sucessfully."
                                }
                            }
                        }   // openshift.withCluster() 
                    }// script  
                } // steps
            } // stage
            stage("Trigger Build.") 
            {
                when{
                    environment name: 'ENVIRONMENT', value: 'INT'
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
                                }
                            }
                        }   // openshift.withCluster() 
                    }// script  
                } // steps
            } // stage
            stage("Tag Images for environment ${params.ENVIRONMENT.toLowerCase()}") 
            {
                when{
                    anyOf {
                        environment name: 'ENVIRONMENT', value: 'AP'
                        environment name: 'ENVIRONMENT', value: 'OP'
                    }
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

                                    def MATIS_ECO_IMAGE_TAG_DEST = "${params.ENVIRONMENT}" == 'AP'? 'PromoteToAP':'PromoteToOP' 
                                    echo "Tag of image to use : ${MATIS_ECO_IMAGE_TAG_DEST}."     
                                    echo "Tag image ${SAFRAN_QUAY_REGISTRY}/${MATIS_ECO_COMPONENT_IMAGE_NAME} with tag : ${MATIS_ECO_IMAGE_TAG_DEST} from tag : ${env.MATIS_IMAGE_TAG}."

                                    openshift.tag(
                                            "${SAFRAN_QUAY_REGISTRY}/${MATIS_ECO_COMPONENT_IMAGE_NAME}:${MATIS_IMAGE_TAG}",
                                            "${SAFRAN_QUAY_REGISTRY}/${MATIS_ECO_COMPONENT_IMAGE_NAME}:${MATIS_ECO_IMAGE_TAG_DEST}",
                                    )

                                    echo "Tag of image : ${SAFRAN_QUAY_REGISTRY}/${MATIS_ECO_COMPONENT_IMAGE_NAME} with tag : ${MATIS_ECO_IMAGE_TAG_DEST} is sucessfully."
                                }
                            }
                        }   // openshift.withCluster() 
                    }// script  
                } // steps
            } // stage
            stage("Deploy ressources  ${params.ENVIRONMENT.toLowerCase()}") 
            {
                steps
                { 
                    echo 'Deploy ressources'  
                } // steps
            } // stage
        }
        post 
        {
            always 
            {
                deleteDir() // Delete the current workspace.
                dir("${env.WORKSPACE}@script") 
                { 
                    deleteDir() // Delete the script workspace, this allow us to update the pipeline script without deleting the builconfig.
                }
            }
        }
}// pipeline