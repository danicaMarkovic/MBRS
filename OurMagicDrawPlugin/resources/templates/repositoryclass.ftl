package ${class.typePackage};

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import application.model.*;


@Repository
public interface ${class.name}Repository extends JpaRepository<${class.name}, Long> {

	
}