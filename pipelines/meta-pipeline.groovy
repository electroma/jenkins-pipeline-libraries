try {
    currentBuild.result = "SUCCESS"

    stage('load libraries') {
        shellInst = new shell()
        pomInst = new pom()
        gitInst = new git()

        repositoryUrl = shellInst.pipe("git config --get remote.origin.url")
        pomVersion = pomInst.version(pwd() + "/pom.xml")
    }

    stage('create package') {
        def commitId = shellInst.pipe("git rev-parse HEAD")

        sh("mvn clean package")

        sshagent([credentialsId]) {
            sh("git tag -a ${pomVersion} -m \"Built version: ${pomVersion}\" ${commitId}")
            sh("git push --tags")
        }
    }

    stage('increment version') {
        def majorVersion = pomInst.majorVersion(pwd() + "/pom.xml")
        def minorVersion = pomInst.minorVersion(pwd() + "/pom.xml").toInteger()
        def patchVersion = pomInst.patchVersion(pwd() + "/pom.xml").toInteger()
        def newVersion = "${majorVersion}.${minorVersion + 1}.0"
        if (patchVersion > 0) {
            newVersion = "${majorVersion}.${minorVersion}.${patchVersion + 1}"
        }
        sh("mvn versions:set -DnewVersion=${newVersion} versions:commit")

        sshagent([credentialsId]) {
            sh("git add pom.xml")
            sh("git commit -m'Bumping version to ${newVersion}'")
            sh("git push origin")
        }
    }
}
catch (err) {
    currentBuild.result = "FAILURE"
    sshagent([credentialsId]) {
        // delete the tag off origin
        sh("git push origin :refs/tags/${pomVersion}")
        sh("git fetch --tags --prune")
    }
    throw err
}