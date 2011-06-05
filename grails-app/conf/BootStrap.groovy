class BootStrap {

    def initService
    def sshd
    def grailsApplication

    def init = { servletContext ->
        println "bootStrap.init ${Thread.currentThread()}"

        initService.init()
        sshd.start()

        synchronized(sshd) {
            sshd.wait()
        }
    }
    
    def destroy = {
        println "bootStrap.destroy ${Thread.currentThread()}"
        sshd.stop()
        synchronized(sshd) {
            sshd.notifyAll()
        }
    }
}
