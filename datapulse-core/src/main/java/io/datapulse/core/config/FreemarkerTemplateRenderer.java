package io.datapulse.core.config;

import freemarker.template.Configuration;
import freemarker.template.Template;
import java.io.StringWriter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FreemarkerTemplateRenderer {

  private final Configuration freemarkerConfiguration;

  public String render(String template, Map<String, String> model) {
    try {
      Template tpl = freemarkerConfiguration.getTemplate(template);
      StringWriter out = new StringWriter();
      tpl.process(model, out);
      return out.toString();
    } catch (Exception ex) {
      throw new IllegalStateException("Ошибка рендера шаблона: " + template, ex);
    }
  }
}
