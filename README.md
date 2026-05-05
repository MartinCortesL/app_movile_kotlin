# Clasificador de animales
## Aplicación movil para identificar animales
### Descripción
En el presente repostirorio se muestra el codigo fuente con el que fue creado la aplicación movil, ésta fue hecha en android studio, utilizando kotlin y jetpack compose.

Para revisar las versiones de las librerias y dependencias usadas en la aplicación, ingrese al archivo gradle.gradle.kts al nivel de aplicación.

Con el fin de identificar a los animales correctamente se entrenó un modelo de CNN con pytorch y un aproximado de 20k de imagenes, dichas imágenes se encuentran en: [Zenodo](https://doi.org/10.5281/zenodo.19926200), 
adémas, los animales que se pueden identificar con dicha aplicación son los siguientes:

Orden Strigiformes

*  Aegolius acadicus
*  Asio flammeus
*  Asio otus
*  Asio stygius
*  Athene cunicularia
*  Bubo virginianus
*  Glaucidium gnoma
*  Megascops kennicottii
*  Megascops trichopsis
*  Micrathene whitneyi
*  Psiloscops flammeolus
*  Strix virgata 
*  Strix occidentalis
*  Strix sartorii
*  Tyto furcata
 
Género Crotalus

*  Crotalus aquilus
*  Crotalus atrox
*  Crotalus basiliscus
*  Crotalus lepidus
*  Crotalus molossus
*  Crotalus polystictus
*  Crotalus pricei
*  Crotalus scutulatus
*  Crotalus willardi

Todos estos animales habitan en el estado de Zacatecas, México.

Por otro lado, el modelo puro entrenado se puede descargar en: [Modelo ResNet50](https://drive.google.com/drive/folders/1EVbwNs3NiT5-lU0dxr1VLg_0NcgxXaHS?usp=drive_link), para visualizar los resultados obtenidos por el modelo [vaya a la sección "Resultados del modelo de IA"](#resultados-del-modelo-de-ia)

### Diseño de la aplicación

### Resultados del modelo de IA
