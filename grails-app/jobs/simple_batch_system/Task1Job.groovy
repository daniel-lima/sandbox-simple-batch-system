package simple_batch_system

class Task1Job {
    
    def timeout = 30000l // execute job once in 30 seconds

    def execute() {
        println "${this} - begin"
        Info i = new Info()
        i.text = "${this} - ${new java.util.Date()}"
        println i.text
        i.save()
        Thread.sleep(3000)
        println "${this} - end"
    }
}
