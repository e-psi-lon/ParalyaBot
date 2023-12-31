import discord
from discord.ext import commands

class Message(discord.ui.Modal):
    def __init__(self, callback, **kwargs):
        super().__init__(title="Quel message voulez-vous envoyer ?")
        for key, value in kwargs.items():
            setattr(self, key, value)
        self.add_item(discord.ui.InputText(label="Message à envoyer", style=discord.InputTextStyle.long))
        self.callback = callback
    
def admin_only():
    async def predicate(ctx: discord.ApplicationContext):
        # Une liste des valeurs d'un Enum
        if ctx.author.guild_permissions.administrator:
            return True
        else:
            await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande.", ephemeral=True)
            return False
    return commands.check(predicate)

async def get_webhook(bot, channel, name) -> discord.Webhook:
    try:
        webhook: discord.Webhook = await [webhook for webhook in await bot.get_channel(channel).webhooks() if webhook.name == name][0].edit(name=name) 
    except IndexError:
        webhook: discord.Webhook = await bot.get_channel(channel).create_webhook(name=name) 
    return webhook