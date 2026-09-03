package com.solace.samples.binder.abc.properties;

public class AuthenticationConfig {

  /**
   * The type of authentication to use. Supported values are "basic" and "oauth". "basic" means that
   * the username and password properties will be used for authentication. "oauth" means that the
   * username property will be used as the client id and the password property will be used as the
   * client secret for authentication. If not specified, no authentication will be used.
   */
  private String type;

  private String username;

  private String password;

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  @Override
  public String toString() {
    return "AuthenticationConfig{" +
        "type='" + type + '\'' +
        ", password='********'" +
        ", username='*****'" +
        '}';
  }
}
