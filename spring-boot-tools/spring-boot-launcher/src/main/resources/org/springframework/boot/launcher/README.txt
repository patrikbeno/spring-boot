Usage:
  java -jar springboot.jar [launcher-options] <artifact> [application-arguments]
  java -jar springboot.jar [launcher-options] <tool> <command> [command-options]
Artifact:
  <groupId>:<artifactId>:<version>[:<packaging>[:<classifier>]]
Tool(s):
  RepositoryManager (list|add|remove)
Launcher Options:
  apphome            : Application home directory
  appname            : Application name
  defaults           : Comma-separated list of URLs specifying hierarchy of MvnLauncher
                       configuration files (*.properties)
  debug              : Enables debug output
  quiet              : Suppresses any output but errors
  statusLine         : Enables status line feedback. Use if the autodetection fails.
  cache              : Directory where all cached files are stored.
  showClasspath      : Dump actual classpath information when constructed.
  offline            : Switches to offline mode. No repository operations are performed
                       and the process relies on cache only.
  updateInterval     : Remote repostitory update interval [seconds]
  verify             : Set this to false to disable downloaded artifact SHA1 verification.
  ignoreCache        : When enabled, cache content is ignored and
                       all artifacts are downloaded again.
  failOnError        : Disable this if you want to try execution despite the errors.
  cacheFileProtocol  : Disable this to use the file-based repository directly
                       (instead of caching them)
  updateReleases     : Enable this to check for updates to released
                       (usually one-time download) artifacts.
  updateSnapshots    : Disable this to ignore snapshot updates.
  execute            : Disable this to only update the application and skip its execution.
  update             : Enable this to ignore cache expiration flags
                       and force updates checking.
  repository         : Name of the repository to use. Such repository must have
                       corresponding entry in your vault (created previously using
                       --MvnLauncher.save=true)
  url                : Maven repository URL
  username           : Maven repository authentication: username
  password           : Maven repository authentication: password
  save               : true
  artifact           : Artifact URI (group:artifact:version)
See:
  https://github.com/patrikbeno/spring-boot/wiki/About
  https://github.com/patrikbeno/spring-boot/wiki/Getting-Started
  https://github.com/patrikbeno/spring-boot/wiki/Reference
Hint:
  Configure your `default` repository using `--MvnLauncher.save=true`
  and `--MvnLauncher.(url|username|password)` options.
  For non-default repository, specify also `--MvnLauncher.repository=<ID>`.
  Once configured, you can switch between repositories using `--MvnLauncher.repository=<ID>` option.
Generate Certificate and Private Key:
  $ mkdir ~/.springboot
  $ openssl genrsa | openssl pkcs8 -topk8 -nocrypt -out ~/.springboot/vault.key
  $ subject="/CN=$(whoami)@$(hostname)/OU=MyDepartment/O=MyOrganization/L=MyLocation/C=US"
  $ openssl req -new -x509 -days 1095 -subj "$subject" -key ~/.springboot/vault.key -out ~/.springboot/vault.crt
