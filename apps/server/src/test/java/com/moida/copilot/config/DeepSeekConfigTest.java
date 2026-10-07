package com.moida.copilot.config;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moida.copilot.llm.api.LlmStatusController;
import com.moida.copilot.llm.infrastructure.DeepSeekLlmGateway;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class DeepSeekConfigTest {
  @Test void selectedProviderIgnoresLocalAndFallbackDestinations(){var env=new MockEnvironment().withProperty("copilot.llm.provider","deepseek").withProperty("copilot.llm.base-url","invalid").withProperty("copilot.llm.fallback.base-url","invalid");var gateway=new LlmConfig().llmGateway(new ObjectMapper(),env);assertInstanceOf(DeepSeekLlmGateway.class,gateway);assertFalse(gateway.enabled());assertTrue(gateway.externalConsentRequired());assertEquals("deepseek-flash",gateway.model());}
  @Test void deepSeekStatusIsConfigurationOnly(){var env=new MockEnvironment().withProperty("copilot.llm.provider","deepseek");var status=(Map<?,?>)new LlmStatusController(env,new ObjectMapper()).status();assertEquals("deepseek",status.get("provider"));assertEquals(false,status.get("configured"));assertEquals(true,status.get("externalConsentRequired"));assertEquals(false,status.get("generationVerified"));assertEquals("none",status.get("probe"));}
  @Test void unknownProviderFailsClosed(){assertThrows(IllegalArgumentException.class,()->new LlmConfig().llmGateway(new ObjectMapper(),new MockEnvironment().withProperty("copilot.llm.provider","other")));}
}
