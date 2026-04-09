PROJET EDITEUR COLLABORATIF
==========================================
 
Gokay Sarp CAVUSOGLU
Campus: Aix- Montperrin
Groupe : [1] — Equipe[A13] 


FONCTIONNEMENT DU PROGRAMME
================================

Ce programme est un editeur collaboratif client-serveur en Java.
Plusieurs clients peuvent se connecter a un serveur et editer un document texte partage.
Le document est une liste de lignes (List<String>).

PROTOCOLE
---------
Les clients et serveurs communiquent par TCP avec des messages texte :
- ADDL i texte : ajouter une ligne a la position i
- RMVL i : supprimer la ligne i
- MDFL i texte : modifier la ligne i
- GETD : obtenir tout le document
- GETL i : obtenir la ligne i
- LINE i texte : reponse du serveur avec le contenu d'une ligne
- DONE : fin de transmission
- ERRL message : erreur

MODE PULL (Tache 1)
-------------------
Le client envoie une requete, le serveur repond.
Pour voir les modifications des autres, le client doit envoyer GETD lui-meme.
Le serveur ne pousse rien.

Lancer :
  Terminal 1 : java -cp out network.impl.ServerCentral 12345
  Terminal 2 : java -cp out client.CLIClient --port 12345

MODE PUSH (Tache 2)
--------------------
Quand un client modifie le document, le serveur envoie automatiquement
la modification a tous les autres clients connectes.
Le message est prefixe par "PUSH " (ex: "PUSH ADDL 1 texte").

Lancer :
  Terminal 1 : java -cp out network.impl.ServerCentralPush 12346
  Terminal 2 : java -cp out client.CLIClient --push --port 12346
  Terminal 3 : java -cp out client.CLIClient --push --port 12346

FEDERATION SIMPLE (Taches 3-4)
-------------------------------
Plusieurs serveurs sont interconnectes.
Quand un client modifie le document sur un serveur, la modification
est transmise aux autres serveurs via des messages PEER_PUSH.
Chaque message a un identifiant unique pour eviter les doublons.

Lancer :
  java -cp out test.FederationTestInProcess

FEDERATION AVEC MAITRE (Tache 5)
---------------------------------
Un serveur est designe comme maitre dans le fichier peers.cfg.
Toutes les ecritures passent par le maitre.
Le maitre attribue un numero de sequence global et diffuse
MASTER_ORDER <seq> <commande> a tous les esclaves.
Les esclaves appliquent les operations dans cet ordre.
Les lectures (GETD, GETL) sont servies localement.

Lancer :
  java -cp out test.MasterFederationTest

DISPATCH / REPARTITION DE CHARGE (Tache 6)
--------------------------------------------
Un serveur dispatch recoit les clients et les redirige vers
un serveur de la federation.
Strategies : ROUND_ROBIN ou LEAST_CONNECTIONS.
Le client envoie CONNECT, le dispatch repond SERVER host port.

TOLERANCE AUX PANNES (Tache 7)
--------------------------------
Si le maitre tombe en panne, les esclaves detectent la perte de connexion.
Un algorithme d'election (Bully) est lance :
- Chaque serveur a une priorite basee sur son numero de port.
- Le serveur avec le port le plus eleve gagne.
- Le gagnant envoie COORDINATOR a tous les autres.
Les donnees sont conservees car elles sont repliquees sur les esclaves.

TESTS
-----
java -cp out test.AllInOneTest pull 3 5    (test convergence pull)
java -cp out test.AllInOneTest push 3 5    (test convergence push)
java -cp out test.FederationTestInProcess   (test federation 3 serveurs)
java -cp out test.MasterFederationTest      (test maitre + dispatch + benchmark + election)