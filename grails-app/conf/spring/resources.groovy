// Place your Spring DSL code here
beans = {
    sshd(Sshd) {
        grailsApplication = ref('grailsApplication')
        springSecurityService = ref('springSecurityService')
    }
}
