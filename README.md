# Open4goods project  
 
[![Build and test](https://github.com/open4good/open4goods/actions/workflows/buildDeploy.yml/badge.svg?branch=main)](https://github.com/open4good/open4goods/actions/workflows/buildDeploy.yml)

The first open source tool that operates ecological scoring, doing its best for the common good. 
 
 * Official user sites. Directly from this github repo to :  
	 * **france** : **[eco-achat.fr](https://eco-achat.fr/)**
	 * **world-wide** : maybe one day...

 * Probably one of the largest [product open data database](https://eco-achat.fr/opendata), at least for french products.
 * Financially contributing to the environmental cause, through a repayment of 20% of the site incomes. 

-----
  
## The project  
  
open4goods project is an open-source and open-data product aggregator, search engine and comparator. It is build upon Maven, Java, SpringBoot, Elasticsearch and some other cool libraries.It is designed to :  
  
 * **crawl and ingest product based datas** (merchant offers, reviews, brands ratings). This is the job of the [crawler](crawler/) sub-project. Designed to ingest any kind of data, if having an UPC code, a BRAND or a MODEL_NAME  
  
 * **aggregate data fragments into well structured product-data**, by verticals. Features are -among others- scoring, attributes merging and conflict detection, comments NLP processing... The business intelligence of product construction is weared in the [aggregation](aggregation/) sub-project, and the orchestration and product data construction is operated through the scalable [API](api/) component.  
  
 * **Renders the data through an open API, and through open data sets**, exposed through the [API](api/) component.  
  
 * Make the truth available for everyone on **officially supported websites**, (only french [eco-achat.fr](https://eco-achat.fr) for now), through the [UI](ui/) component. The affiliation revenue business model is used to deliver ecological compensation, maintain the user service and the open-data delivery  
  
The project will do its best to maximize community and user contributions, with effective and fastest delivery on official websites. We would be happy when we will :  
  
 * provide the hugest set of barcodes in open data 
 * provides and deliver the best price comparison and product information platform, in a collaborative and open-sourced manner.  
  
## <i class="icon-upload"></i> Not only code...  
  
**Mostly**, it is a try and an attempt for an ethical and ecological business model. Made on blue time and after having lost in too much complexity, the actual platform is still missing a lot of cool features :
 * green scoring
 * developper documentation
 * product filtering
 * SEO optimisations
 * A community
 * And so more....
  
**I will try to improve it over time**, please feel free to get your feedback on the [contact page](https://eco-achat.fr/contact), to contribute through code or with your ideas or issues on this github.

  
## <i class="icon-upload"></i> How to contribute  
  
There are several ways to contribute.  
 * **Use it** ! By buying your products on official sites, you "create" money through the affiliation system, that allows to reverse the "by law" 20% environmental compensation. By using it you will also able to provide us some feedback  
 * **Speak about it** ! Communication will be the lack of this Odyssey, any kind of help is welcome. Talking about this the project to your granny or your dog could help us a lot.  
  
 * **Feedback us** ! Ideas (philosophicals or pragmaticals ) are greatly welcome ! Allow us to get the truth (the user's one) with your enhancements, bug report.  
  
 
 * **Development** (Docker, Elastic, Java, SpringBoot, Bootstrap, Jquery, ...)   
  
# Run in dev mode
  
### Software Requirements  
You will need :  
  
- [Java 11+](https://adoptopenjdk.net/)  
- [Maven](http://maven.apache.org/install.html)  
- [Elasticsearch](https://github.com/elastic/elasticsearch), that can be transparently provided through [Docker](https://docs.docker.com/engine/install/) and [Docker Compose](https://docs.docker.com/compose/install/)  
  
- Unfortunately a Linux / Mac os, cause Java Path Separators are hardcoded. Not (really) a tooth against Windows User ;)  
  
### running Elasticsearch / Kibana (docker-compose)  
Elasticsearch is the only store used by the open4goods project. It is packaged with a [https://www.elastic.co/fr/kibana](Kibana) instance in the docker-compose.yml file. Go to the project root, then start the compose file  
```  
$ docker-compose up  
```  
Elastic and kibana should be available on :  
  
* Elastic Search : [http://localhost:9200](http://localhost:9200)  
* Kibana : [http://localhost:5601](http://localhost:5601)  
  
Note that you could have to raise your max vm args to be able to rune the Elastic image  
```    
$ sudo sysctl -w vm.max_map_count=262144  
```    
To permanently set the max virtual memory :  
  
1. edit /etc/sysctl.conf  
2. add line vm.max_map_count=262144  
3. sudo service sysctl restart  
  
### Building the open4goods project from code base  
Jars are not published to any central repo (nor planned to, it seems not to make any particular sense). To build, please go into the project folder. Then :  
```    
$ mvn install 
```  

This will build and run tests, hope in your terminal you'll get a  
```  
[INFO] ------------------------------------------------------------------------  
[INFO] Reactor Summary for parent 0.0.1-SNAPSHOT:  
[INFO]  
[INFO] parent ............................................. SUCCESS [ 2.509 s]  
[INFO] commons ............................................ SUCCESS [ 27.046 s]  
[INFO] crawler ............................................ SUCCESS [ 8.990 s]  
[INFO] aggregation ........................................ SUCCESS [ 7.143 s]  
[INFO] api ................................................ SUCCESS [ 11.289 s]  
[INFO] ui ................................................. SUCCESS [ 7.895 s]  
[INFO] test ............................................... SUCCESS [ 1.496 s]  
[INFO] ------------------------------------------------------------------------  
[INFO] BUILD SUCCESS  
[INFO] ------------------------------------------------------------------------  
[INFO] Total time: 01:07 min  
[INFO] Finished at: 2021-01-31T15:20:30+01:00  
[INFO] ------------------------------------------------------------------------  
  
```  
    
### Launching  
The open4goods project is packaged under the form of several SpringBoot web applications. You will probably want to launch :  
* [API](api) component : Play with the data aspect and the business logic  
* [UI](ui) component : Play with the user interface part of the question  
  
  
#### Command line  
  
From the project root folder, you can now launch the aspects of the platform you are interested in :  
  
**UI**  
```  
$ java -Dspring.profiles.active=dev -jar ui/target/ui-[VERSION].jar  
```  

You should be able to access the open4goods user interface at [http://localhost:8082](http://localhost:8082)  
  
**Api**  

```  
$ java -Dspring.profiles.active=dev -jar api/target/api-[VERSION].jar  
```  

You should be able to access the open4goods API at [http://localhost:8081](http://localhost:8081)  
  
**Crawler**  
You should not need to run a separate crawler, since an embedded one is instanciated in the API. However, if you want to play, or register as a open4goods web scrapper and help us in crawling the world, you could setup an individual crawler node.  

```  
$ java -Dspring.profiles.active=dev -jar target/bin/open4goods-crawler.jar  
```  

You should be able to access the open4goods crawler interface at [http://localhost:8080](http://localhost:8080)  
  
#### Using an IDE  
If using any kind of IDE (tested with Eclipse and Intellij), please import as maven or SpringBoot project, then run or debug from the following Application classes :  
  
* UI : [ui/src/main/java/com/open4goods/ui/Ui.java](ui/src/main/java/com/open4goods/ui/Ui.java)  
* API : [api/src/main/java/com/open4goods/api/Api.java](ui/src/main/java/com/open4goods/ui/Api.java)  
* Crawler : [crawler/src/main/java/com/open4goods/crawler/Crawler.java](ui/src/main/java/com/open4goods/crawler/Crawler.java)  
  
> Don't forget to specify the profile to use (probably you'll want "dev" to run in development mode)  

## Import data
**TODO** : provide a playground dataset
  
### Check dependencies update  
You can use maven to get spring-boot and other dependencies update.  
```  
$ mvn versions:display-dependency-updates -DprocessDependencyManagement=false -DallowIncrementalUpdates=false  
```  
### Elastic space crash  
  
After a space crash, elastic indexes are locked. You can get hand back on them by using :  
```  
curl -XPUT -H "Content-Type: application/json" http://localhost:9200/_all/_settings -d '{"index.blocks.read_only_allow_delete": null}'  
```  
  
