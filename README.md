# Discord2Velocity



This plugin allows you to mutually synchronize the chat between Discord and Velocity Network, like DiscordSRV.



## Installation

This plugin needs to have these dependencies installed:

- JDK 11 (Tested with JDK 17)

- Velocity 3.3 (Tested)

After you have put it in the plugin jar into the plugins folder, restart your Velocity server and wait until the configuration file is generated. Once you have ```config.yaml``` generated, stop the server. After that, you have to put a Discord Bot token and channel name you want your Discord Bot to synchronize the chat to.

## Commands

### ```/velo2disc reload``` 

This command reloads the ```config.yaml```, and reconnects to Discord Bot with the reloaded token and channel name.

## License

This plugin is released under **MIT LICENSE**. However, this project contains some libraries that are licensed under **Apache 2.0 License**.
