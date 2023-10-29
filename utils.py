import discord
import json
import os
import shutil

EMBED_PAGE_DEFAULT = discord.Embed(title="Configuration", description="Quel duo de salon souhaitez vous modifier ? ", color=discord.Color.blurple())
EMBED_CHANNEL_CONFIG = discord.Embed(title="Configuration", description="Choisissez le salon dans lequel les utilisateurs pourront envoyer des messages anonymes, celui dans lequel les messages anonymes seront envoyés, quels utilisateurs seront ignorés par le bot et une séquence d'échappement.\n*Si vous changez le paramètre des utilisateurs veuillez selectionner tout les utilisateurs que vous souhaitez être ignorés, y compris ceux qui était déjà configuré, car il est impossible de définir des valeurs par défaut dans ce type de selecteur.*", color=discord.Color.blurple())\
    .add_field(name="Salon d'envoi des messages", value="", inline=False)\
    .add_field(name="Salon d'envoi des messages anonymes", value="", inline=False)\
    .add_field(name="Utilisateurs ignorés", value="", inline=False)\
    .add_field(name="Séquence d'échappement", value="", inline=False)

def get_list_index(config: list, user_channel: int, anonymous_channel: int) -> int:
    for index, channel in enumerate(config):
        if channel[0] == user_channel and channel[1] == anonymous_channel:
            return index
    return -1

def get_anonymous(server_id: int) -> list[list[int, int, int, list[int | None]]]:
    if not os.path.exists(f"database/{server_id}/anonymous.json"):
        os.makedirs(f"database/{server_id}", exist_ok=True)
        shutil.copy("database/anonymoys.json", f"database/{server_id}/anonymous.json")
    config = json.load(open(f"database/{server_id}/anonymous.json", "r"))
    config = [[channel["user_channel"], channel["anonymous_channel"], channel["last_message_sender"], channel["bypass_anonymous"], channel["escape_sequence"]] for channel in config]
    return config
    
    
def get_user_channels(config: list[list[int, int, int, list[int | None]]]) -> list[int]:
    """
    Récupère les salons dans lesquels les utilisateurs pourront envoyer des messages anonymes
    :param config: La configuration du serveur
    :return: Une liste contenant les id des salons
    """
    channels = []
    for channel in config:
        channels.append(channel[0])
    return channels

def get_anonymous_channels(config: list[list[int, int, int, list[int | None]]]) -> list[int]:
    """
    Récupère les salons dans lesquels les messages anonymes seront envoyés
    :param config: La configuration du serveur
    :return: Une liste contenant les id des salons
    """
    channels = []
    for channel in config:
        channels.append(channel[1])
    return channels

def get_bypassed_users(config: list[list[int, int, int, list[int | None]]], user_channel: int, anonymous_channel: int) -> list[int]:
    """
    Récupère les utilisateurs qui ont le droit de contourner l'anonymat pour un lien de salon donné
    :param config: La configuration du serveur
    :param user_channel: L'id du salon dans lequel les utilisateurs pourront envoyer des messages anonymes
    :param anonymous_channel: L'id du salon dans lequel les messages anonymes seront envoyés
    :return: Une liste contenant les id des utilisateurs
    """
    index = get_list_index(config, user_channel, anonymous_channel)
    if index == -1:
        return []
    return config[index][3]
    
def get_list_with_user_channel(config: list[list[int, int, int, list[int | None]]], user_channel: int) -> list[list[int, int, int, list[int | None]]]:
    """
    Récupère les duos de salon qui ont le salon donné en premier paramètre
    :param config: La configuration du serveur
    :param user_channel: L'id du salon dans lequel les utilisateurs pourront envoyer des messages anonymes
    :return: Une liste contenant les duos de salon
    """
    return [channel for channel in config if channel[0] == user_channel]


def edit_anonymous(config: list, server_id: int):
    config = [{"user_channel": channel[0], "anonymous_channel": channel[1], "last_message_sender": channel[2], "bypass_anonymous":channel[3], "escape_sequence":channel[4]} for channel in config]
    if not os.path.exists(f"database/{server_id}/anonymous.json"):
        os.makedirs(f"database/{server_id}", exist_ok=True)
        shutil.copy("database/anonymous.json", f"database/{server_id}/anonymous.json")
    json.dump(config, open(f"database/{server_id}/anonymous.json", "w"), indent=4)
