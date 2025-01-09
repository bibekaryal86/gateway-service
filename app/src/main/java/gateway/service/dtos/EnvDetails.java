package gateway.service.dtos;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class EnvDetails implements Serializable {
  private String name;
  private String stringValue;
  private List<String> listValue;
  private Map<String, String> mapValue;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getStringValue() {
    return stringValue;
  }

  public void setStringValue(String stringValue) {
    this.stringValue = stringValue;
  }

  public List<String> getListValue() {
    return listValue;
  }

  public void setListValue(List<String> listValue) {
    this.listValue = listValue;
  }

  public Map<String, String> getMapValue() {
    return mapValue;
  }

  public void setMapValue(Map<String, String> mapValue) {
    this.mapValue = mapValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    EnvDetails that = (EnvDetails) o;
    return Objects.equals(name, that.name)
        && Objects.equals(stringValue, that.stringValue)
        && Objects.equals(listValue, that.listValue)
        && Objects.equals(mapValue, that.mapValue);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, stringValue, listValue, mapValue);
  }
}
