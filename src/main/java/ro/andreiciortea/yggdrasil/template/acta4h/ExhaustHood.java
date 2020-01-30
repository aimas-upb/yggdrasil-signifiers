package ro.andreiciortea.yggdrasil.template.acta4h;

import io.vertx.core.http.HttpMethod;

import ro.andreiciortea.yggdrasil.template.annotation.RequestMapping;
import ro.andreiciortea.yggdrasil.template.annotation.Artifact;
import ro.andreiciortea.yggdrasil.template.annotation.ObservableProperty;
import ro.andreiciortea.yggdrasil.template.annotation.RdfAddition;

@Artifact(type = "Exhausthood", additions =
  @RdfAddition(predicates ={"http://www.w3.org/1999/02/22-rdf-syntax-ns#type"}, objects = {"td:Thing"})
)
public class ExhaustHood {

  @ObservableProperty
  public double Energie_Partielle_Hote = 0;

  @ObservableProperty
  public double Energie_Totale_Hote = 0;

  @ObservableProperty
  public double Intensite_Hote = 0;

  @ObservableProperty
  public double Puissance_Hote = 0;

  @ObservableProperty
  public double Tension_Hote = 0;

  @RequestMapping(requestMethod = "PUT", path = "/Energie_Partielle_Hote")
  public double setEnergie_Partielle_Hote(double energie_Partielle_Hote) {
    Energie_Partielle_Hote = energie_Partielle_Hote;
    return this.Energie_Partielle_Hote;
  }

  @RequestMapping(requestMethod = "PUT", path = "/Energie_Totale_Hote")
  public double setEnergie_Totale_Hote(double energie_Totale_Hote) {
    Energie_Totale_Hote = energie_Totale_Hote;
    return this.Energie_Totale_Hote;
  }

  @RequestMapping(requestMethod = "PUT", path = "/Intensite_Hote")
  public double setIntensite_Hote(double intensite_Hote) {
    Intensite_Hote = intensite_Hote;
    return this.Intensite_Hote;
  }

  @RequestMapping(requestMethod = "PUT", path = "/Puissance_Hote")
  public double setPuissance_Hote(double puissance_Hote) {
    Puissance_Hote = puissance_Hote;
    return this.Puissance_Hote;
  }

  @RequestMapping(requestMethod = "PUT", path = "/Tension_Hote")
  public double setTension_Hote(double tension_Hote) {
    Tension_Hote = tension_Hote;
    return this.Tension_Hote;
  }
}
