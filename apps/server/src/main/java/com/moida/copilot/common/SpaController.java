package com.moida.copilot.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaController {
  @GetMapping({"/", "/login", "/teams", "/teams/{team}", "/teams/{team}/knowledge", "/teams/{team}/usage", "/meetings/{meeting}"})
  public String index() { return "forward:/index.html"; }
}
