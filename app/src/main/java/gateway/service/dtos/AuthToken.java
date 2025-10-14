package gateway.service.dtos;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class AuthToken {
  private final AuthTokenPlatform platform;
  private final AuthTokenProfile profile;
  private final List<AuthTokenRole> roles;
  private final List<AuthTokenPermission> permissions;
  private final Boolean isSuperUser;

  @JsonCreator
  public AuthToken(
      @JsonProperty("platform") AuthTokenPlatform platform,
      @JsonProperty("profile") AuthTokenProfile profile,
      @JsonProperty("roles") List<AuthTokenRole> roles,
      @JsonProperty("permissions") List<AuthTokenPermission> permissions,
      @JsonProperty("isSuperUser") boolean isSuperUser) {
    this.platform = platform;
    this.profile = profile;
    this.roles = roles;
    this.permissions = permissions;
    this.isSuperUser = isSuperUser;
  }

  public AuthTokenPlatform getPlatform() {
    return platform;
  }

  public AuthTokenProfile getProfile() {
    return profile;
  }

  public List<AuthTokenRole> getRoles() {
    return roles;
  }

  public List<AuthTokenPermission> getPermissions() {
    return permissions;
  }

  public Boolean getIsSuperUser() {
    return isSuperUser;
  }

  @Override
  public String toString() {
    return "AuthToken{"
        + "platform="
        + platform
        + ", profile="
        + profile
        + ", roles="
        + roles
        + ", permissions="
        + permissions
        + ", isSuperUser="
        + isSuperUser
        + '}';
  }

  public static class AuthTokenPlatform {
    private final long id;
    private final String platformName;

    @JsonCreator
    public AuthTokenPlatform(
        @JsonProperty("id") final long id,
        @JsonProperty("platformName") final String platformName) {
      this.id = id;
      this.platformName = platformName;
    }

    public long getId() {
      return id;
    }

    public String getPlatformName() {
      return platformName;
    }

    @Override
    public String toString() {
      return "AuthTokenPlatform{" + "id=" + id + ", platformName='" + platformName + '\'' + '}';
    }
  }

  public static class AuthTokenProfile {
    private final long id;
    private final String email;

    @JsonCreator
    public AuthTokenProfile(
        @JsonProperty("id") final long id, @JsonProperty("email") final String email) {
      this.id = id;
      this.email = email;
    }

    public long getId() {
      return id;
    }

    public String getEmail() {
      return email;
    }

    @Override
    public String toString() {
      return "AuthTokenProfile{" + "id=" + id + ", email='" + email + '\'' + '}';
    }
  }

  public static class AuthTokenRole {
    private final long id;
    private final String roleName;

    @JsonCreator
    public AuthTokenRole(
        @JsonProperty("id") final long id, @JsonProperty("roleName") final String roleName) {
      this.id = id;
      this.roleName = roleName;
    }

    public long getId() {
      return id;
    }

    public String getRoleName() {
      return roleName;
    }

    @Override
    public String toString() {
      return "AuthTokenRole{" + "id=" + id + ", roleName='" + roleName + '\'' + '}';
    }
  }

  public static class AuthTokenPermission {
    private final long id;
    private final String permissionName;

    @JsonCreator
    public AuthTokenPermission(
        @JsonProperty("id") final long id,
        @JsonProperty("permissionName") final String permissionName) {
      this.id = id;
      this.permissionName = permissionName;
    }

    public long getId() {
      return id;
    }

    public String getPermissionName() {
      return permissionName;
    }

    @Override
    public String toString() {
      return "AuthTokenPermission{"
          + "id="
          + id
          + ", permissionName='"
          + permissionName
          + '\''
          + '}';
    }
  }
}
