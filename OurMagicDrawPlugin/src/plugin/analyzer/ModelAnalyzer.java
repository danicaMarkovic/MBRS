package plugin.analyzer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import org.eclipse.emf.ecore.EClass;
import org.eclipse.emf.ecore.EClassifier;
import org.eclipse.emf.ecore.EEnumLiteral;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EOperation;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EParameter;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.ocl.OCL;
import org.eclipse.ocl.ecore.CallOperationAction;
import org.eclipse.ocl.ecore.EcoreEnvironmentFactory;
import org.eclipse.ocl.ecore.SendSignalAction;
import org.eclipse.ocl.expressions.OCLExpression;
import org.eclipse.ocl.helper.OCLHelper;

import plugin.generator.fmmodel.CascadeType;
import plugin.generator.fmmodel.ComponentType;
import plugin.generator.fmmodel.FMClass;
import plugin.generator.fmmodel.FMEnumeration;
import plugin.generator.fmmodel.FMLinkedCharacteristics;
import plugin.generator.fmmodel.FMModel;
import plugin.generator.fmmodel.FMPersistentCharacteristics;
import plugin.generator.fmmodel.FMProperty;
import plugin.generator.fmmodel.FMType;
import plugin.generator.fmmodel.FetchType;
import plugin.generator.fmmodel.GenerationStrategy;
import plugin.generator.utils.Constants;
import plugin.generator.fmmodel.FMServiceMethods;

import com.nomagic.uml2.ext.jmi.helpers.ModelHelper;
import com.nomagic.uml2.ext.jmi.helpers.StereotypesHelper;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Element;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.EnumerationLiteral;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Generalization;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Package;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Class;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Constraint;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Classifier;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Enumeration;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Property;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.Type;
import com.nomagic.uml2.ext.magicdraw.classes.mdkernel.ValueSpecification;
import com.nomagic.uml2.ext.magicdraw.mdprofiles.Stereotype;
import com.nomagic.uml2.synchronizer.ConstrainedElementHack;

/**
 * Model Analyzer takes necessary metadata from the MagicDraw model and puts it
 * in the intermediate data structure (@see myplugin.generator.fmmodel.FMModel)
 * optimized for code generation using freemarker. Model Analyzer now takes
 * metadata only for ejb code generation
 * 
 * @ToDo: Enhance (or completely rewrite) myplugin.generator.fmmodel classes and
 *        Model Analyzer methods in order to support GUI generation.
 */

public class ModelAnalyzer {
	// root model package
	private Package root;

	// java root package for generated code
	private String filePackage;
	
	private Map<String, String> entities = new HashMap<>();

	private List<String> simpleTypes = new ArrayList<String>();
	public ModelAnalyzer(Package root, String filePackage) {
		super();
		this.root = root;
		this.filePackage = filePackage;
		simpleTypes.add("long");simpleTypes.add("int");simpleTypes.add("boolean");simpleTypes.add("char");
		simpleTypes.add("short");simpleTypes.add("float");simpleTypes.add("double");simpleTypes.add("void");
		simpleTypes.add("String");simpleTypes.add("Date");simpleTypes.add("Boolean");simpleTypes.add("date");
		
		
	}

	public Package getRoot() {
		return root;
	}

	public void prepareModel() throws AnalyzeException {
		FMModel.getInstance().getClasses().clear();
		FMModel.getInstance().getEnumerations().clear();
		processPackage(root, filePackage);
	}

	private void processPackage(Package pack, String packageOwner) throws AnalyzeException {
		// Recursive procedure that extracts data from package elements and stores it in
		// the
		// intermediate data structure
		if (pack.getName() == null)
			throw new AnalyzeException("Packages must have names!");

		String packageName = packageOwner;
		if (pack != root) {
			packageName += "." + pack.getName();
		}

		if (pack.hasOwnedElement()) {
			for (Iterator<Element> it = pack.getOwnedElement().iterator(); it.hasNext();) {
				Element ownedElement = it.next();
				if (ownedElement instanceof Class) {
					Class cl = (Class) ownedElement;
					entities.put(cl.getID(), cl.getName());
					// JOptionPane.showMessageDialog(null, fmClass.getName());
				}

			}

			for (Iterator<Element> it = pack.getOwnedElement().iterator(); it.hasNext();) {
				Element ownedElement = it.next();
				if (ownedElement instanceof Class) {
					Class cl = (Class) ownedElement;
					FMClass fmClass = getClassData(cl, packageName);
					FMModel.getInstance().getClasses().add(fmClass);
					// JOptionPane.showMessageDialog(null, fmClass.getName());
				}

				if (ownedElement instanceof Enumeration) {
					Enumeration en = (Enumeration) ownedElement;
					FMEnumeration fmEnumeration = getEnumerationData(en, packageName);
					FMModel.getInstance().getEnumerations().add(fmEnumeration);
				}
			}

			for (Iterator<Element> it = pack.getOwnedElement().iterator(); it.hasNext();) {
				Element ownedElement = it.next();
				if (ownedElement instanceof Package) {
					Package ownedPackage = (Package) ownedElement;
					if (StereotypesHelper.getAppliedStereotypeByString(ownedPackage, "BusinessApp") != null)
						// only packages with stereotype BusinessApp are candidates for metadata
						// extraction and code generation:
						processPackage(ownedPackage, packageName);
				}
			}

			/**
			 * @ToDo: Process other package elements, as needed
			 */
		}

		Stereotype microserviceStereotype = StereotypesHelper.getAppliedStereotypeByString(pack,
				Constants.packageIdentifier);
		if (microserviceStereotype != null) {
			List<Property> tags = microserviceStereotype.getOwnedAttribute();
			for (Property tag : tags) {
				List<?> value = StereotypesHelper.getStereotypePropertyValue(pack, microserviceStereotype,
						tag.getName());
				if (value.size() > 0) {
					switch (tag.getName()) {
					case "groupId":
						String groupId = (String) value.get(0);
						FMModel.getInstance().setGroupId(groupId);
						break;
					case "artifactId":
						String artifactId = (String) value.get(0);
						FMModel.getInstance().setArtifactId(artifactId);
						break;
					case "version":
						String version = (String) value.get(0);
						FMModel.getInstance().setVersion(version);
						break;
					case "port":
						Integer port = (Integer) value.get(0);
						FMModel.getInstance().setPort(port.toString());
						break;
					case "databaseUrl":
						String databaseUrl = (String) value.get(0);
						FMModel.getInstance().setDatabaseUrl(databaseUrl);
						break;
					case "databaseUsername":
						String databaseUsername = (String) value.get(0);
						FMModel.getInstance().setDatabaseUsername(databaseUsername);
						break;
					case "databasePassword":
						String databasePassword = (String) value.get(0);
						FMModel.getInstance().setDatabasePassword(databasePassword);
						break;
					case "javaVersion":
						Integer javaVersion = (Integer) value.get(0);
						FMModel.getInstance().setJavaVersion(javaVersion);
					}
				}
			}
		}
		// JOptionPane.showMessageDialog(null, "PROVERA PAKETA "+
		// FMModel.getInstance().toString());

	}

	private FMClass getClassData(Class cl, String packageName) throws AnalyzeException {
		if (cl.getName() == null)
			throw new AnalyzeException("Classes must have names!");

		FMClass fmClass = new FMClass(cl.getName(), packageName, cl.getVisibility().toString());
		Iterator<Property> it = ModelHelper.attributes(cl);
		while (it.hasNext()) {
			Property p = it.next();
			FMProperty prop = getPropertyData(p, cl);

			fmClass.addProperty(prop);
		}

		/**
		 * @ToDo: Add import declarations etc.
		 */
		
		importsFMClass(fmClass);

		if(cl.hasGeneralization()) {
			Collection<Generalization> general =cl.getGeneralization();
			general.iterator().next();
			//JOptionPane.showMessageDialog(null, cl.getName()+" " +general.iterator().next().getHumanName());
			//JOptionPane.showMessageDialog(null, cl.getName()+" id" +general.iterator().next().getGeneral().getID()
			//+ " general" +general.iterator().next().getGeneral()
			//+"prva klasa id " +entities.entrySet().stream().findFirst());
			//JOptionPane.showMessageDialog(null, cl.getName()+" " +general.iterator().next().get_representationText());
			;
			if (entities.containsKey(general.iterator().next().getGeneral().getID())) {
				String parentEntity = entities.get(general.iterator().next().getGeneral().getID());
				fmClass.setAncestor(parentEntity);
				//JOptionPane.showMessageDialog(null, cl.getName()+" " +parentEntity);
			}
		}else {
			//JOptionPane.showMessageDialog(null, cl.getName()+" " +"nije nasleddnica");
		}

		Stereotype entityStereotype = StereotypesHelper.getAppliedStereotypeByString(cl, Constants.entityIdentifier);
		if (entityStereotype != null) {
			List<Property> tags = entityStereotype.getOwnedAttribute();// atributi stereotipa
			for (Property tag : tags) {
				List<?> value = StereotypesHelper.getStereotypePropertyValue(cl, entityStereotype, tag.getName());
				if (value.size() > 0) {
					switch (tag.getName()) {
					case "create":
						Boolean create = (Boolean) value.get(0);
						fmClass.setCreate(create);
						break;
					case "update":
						Boolean update = (Boolean) value.get(0);
						fmClass.setUpdate(update);
						break;
					case "delete":
						Boolean delete = (Boolean) value.get(0);
						fmClass.setDelete(delete);
						break;
					}
				}
			}
		}

		// JOptionPane.showMessageDialog(null, fmClass.getName()+ "update
		// "+fmClass.getUpdate()+"create "+fmClass.getCreate()+"delete
		// "+fmClass.getDelete());
			//fmClass.addProperty(prop);	
		
		
		/** @ToDo:
		 * Add import declarations etc. */	
		
		Stereotype stereotypeEntity = StereotypesHelper.getAppliedStereotypeByString(cl, "Entity");
		if (stereotypeEntity != null) {
			List<Property> properties = stereotypeEntity.getOwnedAttribute();
			List<FMServiceMethods> methodsService = new ArrayList<FMServiceMethods>();
			/*Collection<Constraint> cons= stereotypeEntity.get_constraintOfConstrainedElement();
			if (cons.isEmpty()!=true) {
				for (Constraint constraint : cons) {
					ValueSpecification v = constraint.getSpecification();
					OCLExpression<EClassifier> query = null;
					try {
						// create an OCL instance for Ecore
						 OCL<EPackage, EClassifier, EOperation, EStructuralFeature, EEnumLiteral, EParameter, EObject, CallOperationAction, SendSignalAction, org.eclipse.ocl.ecore.Constraint, EClass, EObject> ocl;
						 ocl = OCL.newInstance(EcoreEnvironmentFactory.INSTANCE);

						 // create an OCL helper object
						 OCLHelper<EClassifier, EOperation, EStructuralFeature, org.eclipse.ocl.ecore.Constraint> helper = ocl.createOCLHelper();

						 // set the OCL context classifier
						 
						 helper.setInstanceContext("Entity");

						 query = helper.createQuery(v.toString());

		
					} catch (Exception e) {
						// TODO: handle exception
						JOptionPane.showMessageDialog(null, v.toString());
					}
				}
			}*/
			
			for (Property p : properties) {
				String tagName = p.getName();
				List tag = StereotypesHelper.getStereotypePropertyValue(cl, stereotypeEntity, tagName);
				if (tag.size()>0) {
					Boolean value = true;
					try {
						value = (Boolean) tag.get(0);
					}catch(Exception e) {
						//JOptionPane.showMessageDialog(null, e.getMessage());
					}
					methodsService.add(new FMServiceMethods(tagName, value));
				}
			
			}
			
			fmClass.setServiceMethods(methodsService);
		}
				

//		 JOptionPane.showMessageDialog(null, fmClass.getName()+ "update
//		 "+fmClass.getUpdate()+"create "+fmClass.getCreate()+"delete
//		 "+fmClass.getDelete());
		
		Stereotype uiFormStereotype = StereotypesHelper.getAppliedStereotypeByString(cl, Constants.uiFormIdentifier);
		if(uiFormStereotype != null)
		{
			List<Property> tags = uiFormStereotype.getOwnedAttribute();
			for (Property tag : tags) {
				List<?> value = StereotypesHelper.getStereotypePropertyValue(cl, uiFormStereotype, tag.getName());
				if (value.size() > 0) {
					switch (tag.getName()) {
					case "addItem":
						Boolean add = (Boolean) value.get(0);
						fmClass.setUiAdd(add);
						break;
					case "updateItem":
						Boolean update = (Boolean) value.get(0);
						fmClass.setUiUpdate(update);
						break;
					case "deleteItem":
						Boolean delete = (Boolean) value.get(0);
						fmClass.setUiDelete(delete);
						break;
					}
				}
			}
		}


		return fmClass;
	}

	private FMProperty getPropertyData(Property p, Class cl) throws AnalyzeException {
		String attName = p.getName();
		if (attName == null)
			throw new AnalyzeException("Properties of the class: " + cl.getName() + " must have names!");
		Type attType = p.getType();
		if (attType == null)
			throw new AnalyzeException("Property " + cl.getName() + "." + p.getName() + " must have type!");

		String typeName = attType.getName();
		String typePackage = attType.getPackage().getName();
		if (typeName == null)
			throw new AnalyzeException("Type ot the property " + cl.getName() + "." + p.getName() + " must have name!");

		int lower = p.getLower();
		int upper = p.getUpper();
		//JOptionPane.showMessageDialog(null,attName+" "
		//		+ lower+" "+upper);

		if(typeName.equals("date")) {
			typeName = "Date";
		}
		FMProperty prop = new FMProperty(attName, new FMType(typeName, typePackage), p.getVisibility().toString(),
				lower, upper);

		Stereotype linkedProperty = StereotypesHelper.getAppliedStereotypeByString(p,
				Constants.linkedPropertyIdentifier);
		if (linkedProperty != null) {
			List<Property> tags = linkedProperty.getOwnedAttribute();
			prop.setLinkedCharacteristics(new FMLinkedCharacteristics());
			for (Property tag : tags) {
				List<?> value = StereotypesHelper.getStereotypePropertyValue(p, linkedProperty, tag.getName());
				if (value.size() > 0) {
					switch (tag.getName()) {
					case "fetch":
						EnumerationLiteral enumLiteral = (EnumerationLiteral) value.get(0);
						String enumString = enumLiteral.getName();
						FetchType fetch = FetchType.valueOf(enumString);
						prop.getLinkedCharacteristics().setFetch(fetch);
						break;
					case "mappedBy":
						String mappedBy = (String) value.get(0);
						prop.getLinkedCharacteristics().setMappedBy(mappedBy);
						break;
					case "cascade":
						EnumerationLiteral cascadeLiteral = (EnumerationLiteral) value.get(0);
						String cascadeString = cascadeLiteral.getName();
						CascadeType cascade = CascadeType.valueOf(cascadeString);
						prop.getLinkedCharacteristics().setCascade(cascade);
						break;
					case "optional":
						Boolean optional = (Boolean) value.get(0);
						prop.getLinkedCharacteristics().setOptional(optional);
						break;
					case "orphanRemoval":
						Boolean orphanRemoval = (Boolean) value.get(0);
						prop.getLinkedCharacteristics().setOrphanRemoval(orphanRemoval);
						break;

					}
				}
			}
			Property opposite = p.getOpposite();
			prop.getLinkedCharacteristics().setOppositeUpper(opposite.getUpper());
			
			
			Collection<Stereotype> sH = StereotypesHelper.getStereotypesHierarchy(p);
			for (Stereotype s : sH) {
				List<Property> tagsInherited = s.getOwnedAttribute();
				for (Property tag : tagsInherited) {
					List<?> values = StereotypesHelper.getStereotypePropertyValue(p, s, tag.getName());
					if (values.size() > 0) {
						switch (tag.getName()) {
						case "columnname":
							String columnname = (String) values.get(0);
							prop.setColumnname(columnname);
							break;
						case "label":
							String label = (String) values.get(0);
							prop.setLabel(label);
							break;
						case "component":
							EnumerationLiteral type = (EnumerationLiteral) values.get(0);
							String typeS = type.getName();
							prop.setComponent(ComponentType.valueOf(typeS));
							break;
						}
					}
				}

			}
			/*JOptionPane.showMessageDialog(null,
					prop.getName() + "fetch " + prop.getLinkedCharacteristics().getFetch() + "mappedby "
							+ prop.getLinkedCharacteristics().getMappedBy() + " getter " + prop.getGetter() + " setter "
							+ prop.getSetter());
			*/
		}
		
		Stereotype editableProperty = StereotypesHelper.getAppliedStereotypeByString(p,
				Constants.editablePropertyIdentifier);
		if (editableProperty != null) {
			prop.setIsEditable(true);
			prop.setIsReadOnly(false);
		}else
		{
			prop.setIsEditable(false);
			prop.setIsReadOnly(true);
		}

		Stereotype persistentProperty = StereotypesHelper.getAppliedStereotypeByString(p,
				Constants.persistentPropertyIdentifier);
		if (persistentProperty != null) {
			List<Property> tags = persistentProperty.getOwnedAttribute();
			prop.setPersistentCharacteristics(new FMPersistentCharacteristics());
			for (Property tag : tags) {
				List<?> value = StereotypesHelper.getStereotypePropertyValue(p, persistentProperty, tag.getName());
				if (value.size() > 0) {
					switch (tag.getName()) {
					case "isKey":
						Boolean isKey = (Boolean) value.get(0);
						prop.getPersistentCharacteristics().setKey(isKey);
						break;
					case "isUnique":
						Boolean isUnique = (Boolean) value.get(0);
						prop.getPersistentCharacteristics().setUnique(isUnique);
						break;
					case "length":
						Integer length = (Integer) value.get(0);
						prop.getPersistentCharacteristics().setLength(length);
						break;
					case "precision":
						Integer precision = (Integer) value.get(0);
						prop.getPersistentCharacteristics().setPrecision(precision);
						break;
					case "generationStrategy":
						EnumerationLiteral generationLiteral = (EnumerationLiteral) value.get(0);
						String generationString = generationLiteral.getName();
						GenerationStrategy generation = GenerationStrategy.valueOf(generationString);
						prop.getPersistentCharacteristics().setGeneratedValue(generation);
						break;

					}
				}
			}
			
			
			Collection<Stereotype> sH = StereotypesHelper.getStereotypesHierarchy(p);
			for (Stereotype s : sH) {
				List<Property> tagsInherited = s.getOwnedAttribute();
				for (Property tag : tagsInherited) {
					List<?> values = StereotypesHelper.getStereotypePropertyValue(p, s, tag.getName());
					if (values.size() > 0) {
						switch (tag.getName()) {
						case "columnname":
							String columnname = (String) values.get(0);
							prop.setColumnname(columnname);
							break;
						case "label":
							String label = (String) values.get(0);
							prop.setLabel(label);
							break;
						case "component":
							EnumerationLiteral type = (EnumerationLiteral) values.get(0);
							String typeS = type.getName();
							prop.setComponent(ComponentType.valueOf(typeS));
							break;

						}
					}
				}

			}
			/*JOptionPane.showMessageDialog(null,
					prop.getName() + "isKey " + prop.getPersistentCharacteristics().getKey() + " generationStrategy "
							+ prop.getPersistentCharacteristics().getGeneratedValue() + " length "
							+ prop.getPersistentCharacteristics().getLength() + " precisicon "
							+ prop.getPersistentCharacteristics().getPrecision() + " unique "
							+ prop.getPersistentCharacteristics().getUnique() + " getter " + prop.getGetter()
							+ " setter " + prop.getSetter());
			 */
		}
		
		Stereotype visibleProperty = StereotypesHelper.getAppliedStereotypeByString(p, Constants.visiblePropertyIdentifier);
		if(visibleProperty != null){
			List<Property> tags = visibleProperty.getOwnedAttribute();
			for(Property tag : tags) {
				List<?> value = StereotypesHelper.getStereotypePropertyValue(p, visibleProperty, tag.getName());
				if(value.size() > 0)
				{
					switch (tag.getName()) {
						case "label":
							String label = (String) value.get(0);
							prop.setLabel(label);
							break;
						case "componentType":
							EnumerationLiteral type = (EnumerationLiteral) value.get(0);
							String typeS = type.getName();
							prop.setComponent(ComponentType.valueOf(typeS));
							break;
					}
				}
			}
		}

		Stereotype abstractProperty = StereotypesHelper.getAppliedStereotypeByString(p,
				Constants.abstractPropertyIdentifier);
		if (abstractProperty != null) {
			List<Property> tags = abstractProperty.getOwnedAttribute();
			for (Property tag : tags) {
				List<?> value = StereotypesHelper.getStereotypePropertyValue(p, abstractProperty, tag.getName());
				if (value.size() > 0) {
					switch (tag.getName()) {
						case "columnname":
							String columnname = (String) value.get(0);
							prop.setColumnname(columnname);
							break;

					}
				}
			}
			// JOptionPane.showMessageDialog(null, prop.getName()+ "getter
			// "+prop.getGetter() +"setter "+prop.getSetter());
		}

		return prop;
	}

	private FMEnumeration getEnumerationData(Enumeration enumeration, String packageName) throws AnalyzeException {
		FMEnumeration fmEnum = new FMEnumeration(enumeration.getName(), packageName);
		List<EnumerationLiteral> list = enumeration.getOwnedLiteral();
		for (int i = 0; i < list.size() - 1; i++) {
			EnumerationLiteral literal = list.get(i);
			if (literal.getName() == null)
				throw new AnalyzeException("Items of the enumeration " + enumeration.getName() + " must have names!");
			fmEnum.addValue(literal.getName());
		}
		return fmEnum;
	}

	private void importsFMClass(FMClass cl) {
		// property check
		ArrayList<String> imports = new ArrayList<>();
		String import_str = "";
		for (FMProperty p : cl.getProperties()) {
			import_str = cl.getTypePackage() + "." + p.getType().getName();
			if (!imports.contains(import_str) && import_str != "" && !simpleTypes.contains(p.getType().getName())) {
				imports.add(import_str);
			
			}

		}

		cl.setImportedPackages(imports);

	}

}
