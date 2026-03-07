package cicd.persistence.entity;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RunStatusTest {

  @Test
  void allValuesExist() {
    // Verify all four status values are defined
    assertNotNull(RunStatus.PENDING);
    assertNotNull(RunStatus.RUNNING);
    assertNotNull(RunStatus.SUCCESS);
    assertNotNull(RunStatus.FAILED);
  }

  @Test
  void getLabelReturnsLowercase() {
    assertEquals("pending", RunStatus.PENDING.getLabel());
    assertEquals("running", RunStatus.RUNNING.getLabel());
    assertEquals("success", RunStatus.SUCCESS.getLabel());
    assertEquals("failed", RunStatus.FAILED.getLabel());
  }

  @Test
  void nameReturnsUppercase() {
    assertEquals("PENDING", RunStatus.PENDING.name());
    assertEquals("RUNNING", RunStatus.RUNNING.name());
    assertEquals("SUCCESS", RunStatus.SUCCESS.name());
    assertEquals("FAILED", RunStatus.FAILED.name());
  }

  @Test
  void valueOfFromString() {
    assertEquals(RunStatus.PENDING, RunStatus.valueOf("PENDING"));
    assertEquals(RunStatus.RUNNING, RunStatus.valueOf("RUNNING"));
    assertEquals(RunStatus.SUCCESS, RunStatus.valueOf("SUCCESS"));
    assertEquals(RunStatus.FAILED, RunStatus.valueOf("FAILED"));
  }

  @Test
  void valueOfInvalidThrows() {
    assertThrows(IllegalArgumentException.class,
        () -> RunStatus.valueOf("UNKNOWN"));
  }

  @Test
  void getLabelIsDifferentFromName() {
    // getLabel() returns lowercase, name() returns uppercase
    for (RunStatus status : RunStatus.values()) {
      assertEquals(status.name().toLowerCase(), status.getLabel());
      assertNotEquals(status.name(), status.getLabel());
    }
  }

  @Test
  void toStringReturnsSameName() {
    // Default Enum.toString() returns name()
    assertEquals("SUCCESS", RunStatus.SUCCESS.toString());
    assertEquals("FAILED", RunStatus.FAILED.toString());
  }

  @Test
  void valuesReturnsAllFour() {
    assertEquals(4, RunStatus.values().length);
  }
}
