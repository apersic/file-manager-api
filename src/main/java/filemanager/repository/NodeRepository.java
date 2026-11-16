package filemanager.repository;

import filemanager.entity.NodeEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NodeRepository extends JpaRepository<NodeEntity, Long>, NodeRepositoryCustom {

  Optional<NodeEntity> findByParentIdIsNull();
}
