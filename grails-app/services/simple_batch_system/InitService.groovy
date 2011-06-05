package simple_batch_system

class InitService {

    def springSecurityService
    
    public void init() {
        def users = User.findAll()
        def roles = [:]
        if (!users || users.size() <= 0) {
            for (authority in ['admin', 'user']) {
                def r = new Role(authority: authority)
                roles.put(authority, r)
                r.save()
            }

            def createUser = {idx, role ->
                def password = springSecurityService.encodePassword(role.authority)
                def user = new User(username: "${role.authority}${idx}", password: password, enabled: true, accountExpired: false, accountLocked: false, passwordExpired: false)
                user.save()
                
                UserRole.create(user, role)
            }
            
            for (i in (1..10)) {
                createUser(i, roles.user)
            }

            createUser(0, roles.admin)
        }
    }

}