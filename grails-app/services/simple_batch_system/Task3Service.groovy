package simple_batch_system

class Task3Service {


    public void execute() {
        println "${this} - begin"
        Info i = new Info()
        i.text = "${this} - ${new java.util.Date()}"
        println i.text
        i.save()
        println "${this} - end"
    }


}