package footsiebot.database;

import footsiebot.nlp.ParseResult;
import footsiebot.datagathering.ScrapeResult;
import footsiebot.ai.*;
import java.time.LocalDateTime;
import java.util.ArrayList;

public interface IDatabaseManager {

  public boolean storeScraperResults(ScrapeResult sr);

  public boolean storeQuery(ParseResult pr, LocalDateTime date);

  public String[] getFTSE(ParseResult pr);

  public ArrayList<Company> getAICompanies();

  public ArrayList<Group> getAIGroups();
  //unused
  public void storeAICompanies(ArrayList<Company> companies);
  // unused
  public void storeAIGroups(ArrayList<Group> groups);

  public String[] getCompaniesInGroup(String groupName);

  public void onSuggestionIrrelevant(Company company, AIIntent intent, boolean isNews);

  public ArrayList<String> detectedImportantChange(Float threshold);
}
