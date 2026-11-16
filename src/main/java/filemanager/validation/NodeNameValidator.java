package filemanager.validation;

import filemanager.domain.NodeName;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NodeNameValidator implements ConstraintValidator<ValidNodeName, String> {

  @Override
  public boolean isValid(String name, ConstraintValidatorContext context) {
    return NodeName.passesShapeConstraint(name);
  }
}
