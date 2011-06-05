package simple_batch_system

class Task2Job {
    
    def timeout = 120000l // execute job once in 2 minutes

    def execute() {
        println "${this} - begin"
        Info i = new Info()
        i.text = "${this} - ${new java.util.Date()}"
        println i.text
        i.save()
        Thread.sleep(10000)
        println "${this} - end"
    }
}
