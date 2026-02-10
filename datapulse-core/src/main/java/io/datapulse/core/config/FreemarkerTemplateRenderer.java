package io.datapulse.core.config;

import freemarker.template.Configuration;
import freemarker.template.Template;
import io.datapulse.domain.MessageCodes;
import io.datapulse.domain.exception.AppException;
import java.io.StringWriter;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FreemarkerTemplateRenderer {

  private final Configuration freemarkerConfiguration;

  public String render(String templateName, Map<String, Object> model) {
    try {
      Template template = freemarkerConfiguration.getTemplate(templateName);

      StringWriter writer = new StringWriter();
      template.process(model, writer);

      return writer.toString();
    } catch (Exception ex) {
      throw new TemplateRenderException(templateName, ex);
    }
  }

  private static final class TemplateRenderException extends AppException {

    private TemplateRenderException(String templateName, Throwable cause) {
      super(
          cause,
          HttpStatus.INTERNAL_SERVER_ERROR,
          MessageCodes.TEMPLATE_RENDER_FAILED,
          templateName
      );
    }
  }
}
