# akka-rabbitmq
Utilisation de la bibliothèque akka-stream pour lire un document .tsv et publication de son contenu dans un message broker RabbitMQ


## Configuration de rabbitmq
```sh
# Démarrage de rabbitmq
brew services start rabbitmq

# Pour vérifier que le serveur fonctionne correctement
rabbitmqctl status

# Pour ajouter les lignes de commande rabbitmq 
export PATH=/usr/local/sbin:$PATH

# Configuration de rabbitmq
rabbitmqctl add_user admin admin
rabbitmqctl set_user_tags admin administrator
rabbitmqctl set_permissions -p admin ".*" ".*" ".*"
rabbitmqctl add_vhost myvhost
rabbitmqctl set_permissions -p myvhost admin ".*" ".*" ".*"
rabbitmqadmin declare exchange --vhost=myvhost name=exchange type=direct --user=admin --password=admin
rabbitmqadmin declare queue --vhost=myvhost name=queue durable=true --user=admin --password=admin
rabbitmqadmin declare binding --vhost=myvhost source=exchange destination=queue destination_type=queue routing_key="foobar" --user=admin --password=admin

```

## Téléchargement des données
Les données sont à placer dans `src/main/resources/title.basics.tsv` et peuvent être téléchargées sur : https://datasets.imdbws.com/title.basics.tsv.gz

