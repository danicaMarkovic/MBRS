package plugin.generator;

import java.io.IOException;
import java.io.Writer;
import java.util.HashMap;
import java.util.Map;

import javax.swing.JOptionPane;

import freemarker.template.TemplateException;
import plugin.generator.fmmodel.FMModel;
import plugin.generator.options.GeneratorOptions;

public class HomePageJsGenerator extends BasicGenerator{

	public HomePageJsGenerator(GeneratorOptions generatorOptions) {
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
		
		Writer out;
		Map<String, Object> context = new HashMap<String, Object>();
		try {
			out = getWriter("","");
			if(out !=  null)
			{
				context.clear();
				context.put("classes", FMModel.getInstance().getClasses());
				getTemplate().process(context, out);
				out.flush();
			}
		}catch (TemplateException e) {
			JOptionPane.showMessageDialog(null, e.getMessage());
		} catch (IOException e) {
			JOptionPane.showMessageDialog(null, e.getMessage());
		}
	}
}
