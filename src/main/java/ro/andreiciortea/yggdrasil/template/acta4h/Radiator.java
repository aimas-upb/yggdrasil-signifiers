package ro.andreiciortea.yggdrasil.template.acta4h;

import io.vertx.core.http.HttpMethod;

import ro.andreiciortea.yggdrasil.template.annotation.RequestMapping;
import ro.andreiciortea.yggdrasil.template.annotation.Artifact;
import ro.andreiciortea.yggdrasil.template.annotation.ObservableProperty;
import ro.andreiciortea.yggdrasil.template.annotation.RdfAddition;

@Artifact(type = "Radiator", additions =
  @RdfAddition(predicates ={"http://www.w3.org/1999/02/22-rdf-syntax-ns#type"}, objects = {"td:Thing"})
)
public class Radiator {

  @ObservableProperty
  public String command = "";

  @ObservableProperty
  public String instruction = "";

  @ObservableProperty
  public String effectiveInstruction = "";

  @ObservableProperty
  public String instructionChange = "";

  @ObservableProperty
  public String window = "";

  @ObservableProperty
  public String baseMode = "";

  @ObservableProperty
  public String effectiveMode = "";

  @ObservableProperty
  public String presence = "";

  @ObservableProperty
  public double temperature = 0;

  @RequestMapping(requestMethod = "PUT", path = "/Command")
  public String setCommand(String command) {
    this.command = command;
    return this.command;
  }

  @RequestMapping(requestMethod = "PUT", path = "/Instruction")
  public String setInstruction(String instruction) {
    this.instruction = instruction;
    return this.instruction;
  }

  @RequestMapping(requestMethod = "PUT", path = "/EffectiveInstruction")
  public String setEffectiveInstruction(String effectiveInstruction) {
    this.effectiveInstruction = effectiveInstruction;
    return this.effectiveInstruction;
  }

  @RequestMapping(requestMethod = "PUT", path = "/InstructionChange")
  public String setInstructionChange(String instructionChange) {
    this.instructionChange = instructionChange;
    return this.instructionChange;
  }

  @RequestMapping(requestMethod = "PUT", path = "/Window")
  public String setWindwow(String window) {
    this.window = window;
    return this.window;
  }

  @RequestMapping(requestMethod = "PUT", path = "/BaseMode")
  public String setBaseMode(String baseMode) {
    this.baseMode = baseMode;
    return this.baseMode;
  }

  @RequestMapping(requestMethod = "PUT", path = "/EffectiveMode")
  public String setEffectiveMode(String effectiveMode) {
    this.effectiveMode = effectiveMode;
    return this.effectiveMode;
  }

  @RequestMapping(requestMethod = "PUT", path = "/Presence")
  public String setPresence(String presence) {
    this.presence = presence;
    return this.presence;
  }

  @RequestMapping(requestMethod = "PUT", path = "/Temperature")
  public double setTemperature(double temperature) {
    this.temperature = temperature;
    return this.temperature;
  }
}
