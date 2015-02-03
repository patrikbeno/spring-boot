Usage: java -jar springboot.jar [launcher-options] <artifact> [launcher-options | application-options]
Artifact:
  <groupId>:<artifactId>:<version>[:<packaging>[:<classifier>]]
Launcher Options:
  MvnLauncher.apphome            : Application home directory
  MvnLauncher.appname            : Application name
  MvnLauncher.defaults           : Comma-separated list of URLs specifying hierarchy of MvnLauncher
                                   configuration files (*.properties)
  MvnLauncher.debug              : Enables debug output
  MvnLauncher.quiet              : Suppresses any output but errors
  MvnLauncher.statusLine         : Enables status line feedback. Use if the autodetection fails.
  MvnLauncher.cache              : Directory where all cached files are stored.
  MvnLauncher.showClasspath      : Dump actual classpath information when constructed.
  MvnLauncher.offline            : Switches to offline mode. No repository operations are performed
                                   and the process relies on cache only.
  MvnLauncher.updateInterval     : Remote repostitory update interval [seconds]
  MvnLauncher.verify             : Set this to false to disable downloaded artifact SHA1 verification.
  MvnLauncher.ignoreCache        : When enabled, cache content is ignored and
                                   all artifacts are downloaded again.
  MvnLauncher.failOnError        : Disable this if you want to try execution despite the errors.
  MvnLauncher.cacheFileProtocol  : Disable this to use the file-based repository directly
                                   (instead of caching them)
  MvnLauncher.updateReleases     : Enable this to check for updates to released
                                   (usually one-time download) artifacts.
  MvnLauncher.updateSnapshots    : Disable this to ignore snapshot updates.
  MvnLauncher.execute            : Disable this to only update the application and skip its execution.
  MvnLauncher.update             : Enable this to ignore cache expiration flags
                                   and force updates checking.
  MvnLauncher.repository         : Name of the repository to use. Such repository must have
                                   corresponding entry in your vault (created previously using
                                   --MvnLauncher.save=true)
  MvnLauncher.url                : Maven repository URL
  MvnLauncher.username           : Maven repository authentication: username
  MvnLauncher.password           : Maven repository authentication: password
  MvnLauncher.save               : true
  MvnLauncher.artifact           : Artifact URI (group:artifact:version)
See:
  https://github.com/patrikbeno/spring-boot/wiki/About
  https://github.com/patrikbeno/spring-boot/wiki/Getting-Started
  https://github.com/patrikbeno/spring-boot/wiki/Reference
Download:
    https://github.com/patrikbeno/spring-boot/raw/MvnLauncherDist/springboot.jar
Hint:
  Configure your `default` repository using `--MvnLauncher.save=true`
  and `--MvnLauncher.(url|username|password)` options.
  For non-default repository, specify also `--MvnLauncher.repository=<ID>`.
  Once configured, you can switch between repositories using `--MvnLauncher.repository=<ID>` option.
Generate Certificate and Private Key:
  $ cd ~/.springboot
  $ subject="/CN=MyCommonName/OU=MyDepartment/O=MyOrganization/L=MyLocation/C=US"
  $ openssl genrsa -out vault.key
  $ openssl req -new -key vault.key -out vault.csr -subj $subject
  $ openssl req -new -x509 -key vault.key -out vault.crt -days 1095 -subj $subject




