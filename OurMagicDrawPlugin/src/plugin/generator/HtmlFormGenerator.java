package plugin.generator;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JOptionPane;

import freemarker.template.TemplateException;
import plugin.generator.fmmodel.FMClass;
import plugin.generator.fmmodel.FMEnumeration;
import plugin.generator.fmmodel.FMLinkedCharacteristics;
import plugin.generator.fmmodel.FMModel;
import plugin.generator.fmmodel.FMProperty;
import plugin.generator.options.GeneratorOptions;

public class HtmlFormGenerator extends BasicGenerator {

	public HtmlFormGenerator(GeneratorOptions generatorOptions) {
		super(generatorOptions);
		// TODO Auto-generated constructor stub
	}
	
	public void generate() {
		try {
			super.generate();
		}catch (IOException e)
		{
			JOptionPane.showMessageDialog(null, e.getMessage());
		}
		
		List<FMClass> classes = FMModel.getInstance().getClasses();
		List<FMEnumeration> enumerations = new ArrayList<FMEnumeration>();
		List<FMProperty> linked = new ArrayList<FMProperty>();
		
		for(FMClass cl : classes)
		{
			if(cl.getUiAdd())
			{
				List<FMProperty> editProperties = new ArrayList<FMProperty>();
				
				for(FMProperty p : cl.getProperties())
				{
					FMLinkedCharacteristics lc = p.getLinkedCharacteristics();
					if(p.getIsEditable())
					{
						editProperties.add(p);
						
						if(p.getType().getTypePackage().equals("Data"))
						{
							for(FMEnumeration en : FMModel.getInstance().getEnumerations())
							{
								if(p.getType().getName().equals(en.getName()))
								{
									enumerations.add(en);
								}
							}
							
						}
						
					}else if (lc!=null) {
						
						if (lc.getOppositeUpper() == -1 && p.getUpper() == -1) {//many to many
							
						}else if (lc.getOppositeUpper() == 1 && p.getUpper() == -1) {//one to many
							//linked.add(p);
						}else if (lc.getOppositeUpper() == -1 && p.getUpper() == 1) {//many to one
							linked.add(p);
						}else {//one to one
							//linked.add(p);
						}
						
						
						
					}
					
				}
				
				Writer out;
				Map<String, Object> context = new HashMap<String, Object>();
				try {
					out = getWriter(cl.getName(), cl.getTypePackage());
					if(out != null)
					{
						context.clear();
						context.put("classes", FMModel.getInstance().getClasses());
						context.put("class", cl);
						context.put("addAllowed", cl.getUiAdd());
						context.put("properties", editProperties);
						context.put("enumerations", enumerations);
						context.put("linkedProperties", linked);
						getTemplate().process(context, out);
						out.flush();
					}
				}catch (IOException e) {
					JOptionPane.showMessageDialog(null, e.getMessage());
				} catch (TemplateException e) {
					// TODO Auto-generated catch block
					JOptionPane.showMessageDialog(null, e.getMessage());
				}
			}
		}
	}


}
