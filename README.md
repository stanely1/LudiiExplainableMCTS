# LudiiExplainableMCTS

Explainable MCTS with Enhancements for Ludii GGP System

## Dependencies

This project requires the following tools:

* `gradle`
* `java 21`
* `wget`

### Installation Guides

#### Debian / Ubuntu

```bash
# Install Gradle (using SDKMAN for latest version)
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install gradle

# Install Java 21 (OpenJDK) and wget
sudo apt install wget openjdk-21-jdk -y
```

#### Fedora

```bash
# Install Gradle and wget
sudo dnf install wget gradle -y

# Install Java 21 (OpenJDK)
sudo dnf install java-21-openjdk-devel -y
```

#### Arch

```bash
# Install wget, gradle and Java 21 from official repos or AUR
sudo pacman -S wget gradle jdk-openjdk --noconfirm

# Optional: Set Java 21 as default if multiple versions installed
sudo archlinux-java set java-21-openjdk
```

### Verify installations

```bash

# Verify installations
gradle -v
java -version
```
