# rule-based-AI-regulator

A regulator based on different heuristics.

## Installation & Execution
1) Load and install all prerequisites
2) Run `gradle fatJar`, which will produce a .jar in `/build/libs/`
3) Execute via `java -jar heuristic-regulator-1.0-SNAPSHOT.jar [-c/--config config.file] [-dc/--dbconfig dbconfig.file]`

If no path argument is given, the OS' Temp directory is used. The path is only supposed to point to the directory, not the files.

## Config
If no config-path is given the controller uses `heureg.config` in the root directory

| setting | description | default |
|---------|-------------|---------|
|udsockets_use_temp|if set to `true` udsockets_path will be ignored and socket files will be created and used inside the OS' temp directory|`true`|
|udsockets_dir|sets the directory in which socket files will be created and used| - |
|log_dir|sets the directory in which log files will be created|`./logs`|
|max_threads|sets the number of regulator threads and connection-pool size|`5`|
|queue_size|sets the maximum task-queue size|`1000`|
|model_dir|sets the directory for (knowledge-)models|`./models`|
|preloaded_models|list of models to be loaded upon process start. E.g. `TechnischeLogistikSS22`|-|


## DB Config
If no config-path is given the controller uses `db.config` in the root directory

| setting | description | default |
|---------|-------------|---------|
|mariadb_pipe_name|(Windows only) name of the named pipe|`MariaDB`|
|db_name|database name for MariaDB|`genDB`|
|db_user|username for MariaDB|`root`|
|db_password|user-password for MariaDB|`admin`|
|db_host|address for MariaDB|`localhost`|
|db_port|port for MariaDB|`3306`|
|db_socket|(non-Windows only) filepath to MariaDB socket file, if local socket is used|`-`|
