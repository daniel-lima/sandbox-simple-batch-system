/*
* Copyright 2011 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
import org.apache.sshd.SshServer
import org.apache.sshd.server.Command
import org.apache.sshd.server.CommandFactory
import org.apache.sshd.server.command.ScpCommandFactory
import org.apache.sshd.server.Environment
import org.apache.sshd.server.ExitCallback
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider

import org.apache.sshd.server.PasswordAuthenticator

import simple_batch_system.User
import simple_batch_system.UserRole
import simple_batch_system.Role

import org.apache.log4j.Logger

/**
* @author Daniel Henrique Alves Lima
*/
class Sshd {

    private Logger log = Logger.getLogger(getClass())
    private SshServer sshd
    private Map serviceClasses
    private Map jobClasses

    def port
    def springSecurityService
    def grailsApplication

    public void start() {
        assert springSecurityService != null
        this.sshd = SshServer.setUpDefaultServer()
        sshd.port = port? port as int : 8123
        sshd.keyPairProvider = new SimpleGeneratorHostKeyProvider('hostkey.ser')

        def adminRole = null
        User.withTransaction {
            adminRole = Role.findByAuthority('admin')
            assert adminRole != null
        }

        serviceClasses = [:]
        jobClasses = [:]
        def buildMap = {map, grailsClasses->
            for (grailsClass in grailsClasses) {
                map[grailsClass.propertyName] = grailsClass.clazz
            }
        }
        def jobClassesMethod = null
        try {
            grailsApplication.taskClasses
            jobClassesMethod = 'taskClasses'
        } catch (Exception e) {
            jobClassesMethod = 'jobClasses'
        }

        buildMap(serviceClasses, grailsApplication.serviceClasses)
        buildMap(jobClasses, grailsApplication[jobClassesMethod])

        sshd.passwordAuthenticator = {username, password, session ->
            log.debug "username ${username}"
            def allowed = false
            User.withTransaction {
                def user = User.findByUsername(username)
                log.debug "user ${user}"
                if (user && UserRole.get(user.id, adminRole.id)) {
                    def encPass = springSecurityService.encodePassword(password)
                    allowed = encPass.equals(user.password)
                }
            }
            log.debug "allowed ${allowed}"
            return allowed
        } as PasswordAuthenticator

        sshd.commandFactory = new ScpCommandFactory(
            {command ->
                command = command.tokenize(',')
                def cmd
                cmd = [
                    input: null, output: null, error:null, callback: null,
                    setExitCallback: {cb -> cmd.callback = cb},
                    setInputStream: {is -> cmd.input = is as InputStream},
                    setOutputStream: {out -> cmd.output = new PrintStream(out)},
                    setErrorStream: {err -> cmd.error = new PrintStream(err)},
                    destroy: {},
                    start: {env ->                       
                        Thread t = new Thread(
                            {
                                int result = -1
                                try {
                                    Set commandSet = new HashSet(command)
                                    commandSet.removeAll(serviceClasses.keySet())
                                    commandSet.removeAll(jobClasses.keySet())
                                    if (commandSet.size() > 0) {
                                        throw new IllegalArgumentException("Invalid commands ${commandSet}; Available services are ${serviceClasses.keySet()}; Available tasks are ${jobClasses.keySet()}")
                                    }
                                    
                                    for (c in command) {
                                        cmd.output.println "Executing ${c}"
                                        if (serviceClasses.containsKey(c)) {
                                            def service = grailsApplication.mainContext.getBean(c)
                                            service.execute()
                                        } else {
                                            def jobClass = jobClasses[c]
                                            jobClass.triggerNow([:])
                                        }
                                        cmd.output.println 'Done!'
                                        cmd.output.flush()
                                    }
                                    result = 0
                                } catch (Exception e) {
                                    log.error("Executing ${command}", e)
                                    e.printStackTrace(cmd.error)
                                    cmd.error.flush()
                                    return
                                } finally {
                                    try {
                                        cmd.error.close()
                                        cmd.output.close()
                                        cmd.input.close()
                                    } finally {
                                        cmd.callback.onExit(result)
                                    }
                                }
                            } as Runnable)
                        t.start()
                    }
                ]

                return cmd as Command
            } as CommandFactory
        )
        
        sshd.start()
    }

    public void stop() {
        sshd?.stop()
        sshd = null
    }
    

}