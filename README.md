# Horstify

HoRStify is a smart contract static analyis tool for dependency based security patterns. 
The analysis specification is written in the [HoRSt](https://secpriv.wien/horst/) specification language.


### Getting Started

**Prerequisites to Run HoRStify:**
- `souffle` (tested with Souffle 2.3)
- `Java 14+`

A packeged version of HoRStify can be found in `evaluation/executables/`

**Prerequisites to Test HoRStify:**
- See `Pipfile` or run `pipenv sync`

Test scripts can be found in `evaluation/`. 

**Prerequisites to Build HoRStify:**
- `maven`
- Install `horst` dependency in maven: 
    ```commandline
    mvn
    install:install-file
    -Dfile=evaluation/executables/horst-0.4.0-CSF2023.jar
    -DgroupId=wien.secpriv
    -DartifactId=horst
    -Dversion=0.4.0-CSF2023
    -Dpackaging=jar
    -DgeneratePom=true
    ```
- Run maven with `pom.xml` for other dependencies


### Evaluation Scripts
Scripts are meant to be executed inside the `evaluation/` directory.
Evaluation results can be created with `execute-horstify.py` and `execute-horstify-prepared.py`. 
The script `execute-securify.py` creates likewise evaluation results for Securify.
The other scripts implement mainly utility functions for extended evalutation.
