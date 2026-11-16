package filemanager.entity;

import filemanager.domain.NodeKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@Entity
@Table(name = "node")
public class NodeEntity extends EntityBase {

  @Column(name = "parent_id")
  private Long parentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private NodeKind kind;

  @Column(nullable = false, length = 255)
  private String name;

  @Column(name = "name_key", nullable = false, insertable = false, updatable = false, length = 255)
  private String nameKey;

  @Column(
      name = "parent_kind",
      nullable = false,
      insertable = false,
      updatable = false,
      length = 16)
  private String parentKind;
}
