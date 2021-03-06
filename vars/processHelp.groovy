#!/usr/bin/env groovy
import com.claimvantage.sjsl.Help

def call(Map parameters = [:]) {
    
    Help h
    if (parameters.help) {
        h = (Help) parameters.help;
    } else {
        h = new Help(parameters.spaceKey, parameters.rootPageId, parameters.repository)
    }
    
    String branch = parameters.branch ?: 'master'
    
    echo "Process help ${h.spaceKey}/${h.rootPageId} into ${h.repository} only for branch ${branch}"
    
    if (env.BRANCH_NAME == branch) {

        withCredentials([usernameColonPassword(credentialsId: env.CONFLUENCE_CREDENTIAL_ID, variable: 'USERPASS')]) {

            echo "... extract from Confluence"

            sh """
            curl -u "$USERPASS" "https://wiki.claimvantage.com/rest/scroll-html/1.0/sync-export?exportSchemeId=-7F00010101621A20869A6BA52BC63995&rootPageId=${h.rootPageId}" > exportedHelp.zip
            """
        }

        sshagent (credentials: [env.GITHUB_CREDENTIAL_ID]) {
            
            def helpFixer = "hf.jar"
            
            if (! fileExists(helpFixer)) {
                // Using Jenkins GitHub Personal Access Token to access private repo asset through API
                withCredentials([string(credentialsId: 'jenkins-github-api-token', variable: 'githubToken')]) {
                    // On this case the id of the file for respective release
                    // Not searching to keep it simple for now given this step might be deprecated in near future
                    def assetId = "10781420"
                    
                    sh """
                    curl \
                    --header "Accept: application/octet-stream" \
                    --location \
                    --remote-header-name \
                    --silent \
                    --show-error \
                    --user \
                    :${githubToken} \
                    https://api.github.com/repos/claimvantage/ant-help-fixer-2/releases/assets/${assetId} \
                    --output ${helpFixer}
                    """
                }
            }

            echo "... run fixer"

            // Backslashes needed for $ that are not tokens inside all of this script
            // To update correctly the help is necessary to do
            // 1) rm -rf ${h.repository}/*
            // 2) unzip -o optimizedHelp.zip -d ${h.repository}
            // in order to update the repository with deletions as well
            sh """
            java -jar ${helpFixer} -s exportedHelp.zip -t optimizedHelp.zip -k ${h.spaceKey}
            if [ -d ${h.repository} ]; then rm -rf ${h.repository}; fi
            git clone git@github.com:claimvantage/${h.repository}.git
            rm -rf ${h.repository}/*
            which unzip || ( apt-get update -y && apt-get install unzip -y )
            unzip -o optimizedHelp.zip -d ${h.repository}
            """

            echo "... commit if necessary"

            // Avoid build breaking when nothing has changed so nothing to commit
            sh """
            cd ${h.repository}
            git add --all
            git config user.name "Jenkins"
            git config user.email "jenkins@claimvantage.com"
            if [ -z "\$(git status --porcelain)" ]; then
                echo "No help changes to commit"
            else 
                echo "Help changes to commit"
                git commit -m "Committed by Jenkins >> ${env.BRANCH_NAME} b#${env.BUILD_NUMBER}"
                git push
            fi
            cd ..
            rm -rf ${h.repository}
            """
        }
    } else {
        echo "... not processed because branch name was ${env.BRANCH_NAME}"
    }
    
    return this
}
