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

  }
}
