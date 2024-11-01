from typing import Type

import discord
from discord.ext import commands
from shared.enums import Users


class Message(discord.ui.Modal):
    """
    This is a subclass of discord.ui.Modal. It represents a modal dialog box that prompts the user to enter a message.
    The entered message is then processed by a callback function.

    Attributes:
        callback_function: A function that processes the entered message.
    """
    def __init__(self, callback, **kwargs):
        super().__init__(title="Quel message voulez-vous envoyer ?")
        for key, value in kwargs.items():
            setattr(self, key, value)
        self.add_item(discord.ui.InputText(label="Message à envoyer", style=discord.InputTextStyle.long))
        self.callback_function = callback

    async def callback(self, interaction: discord.Interaction):
        await self.callback_function(self, interaction)


def admin_only():
    async def predicate(ctx: discord.ApplicationContext):
        if ctx.author.id in Users:
            return True
        else:
            await ctx.respond("Vous n'avez pas la permission d'utiliser cette commande.", ephemeral=True)
            return False
    return commands.check(predicate)


async def get_webhook(bot, channel, name) -> discord.Webhook:
    try:
        webhook: discord.Webhook = await \
            [webhook for webhook in await bot.get_channel(channel).webhooks() if webhook.name == name][0].edit(
                name=name)
    except IndexError:
        webhook: discord.Webhook = await bot.get_channel(channel).create_webhook(name=name)
    return webhook


class Retry(discord.ui.View):
    """
    This is a subclass of discord.ui.View. It represents a view that provides a retry button to the user.
    When the retry button is clicked, it reopens the modal dialog box.

    Attributes:
        modal: A subclass of discord.ui.Modal. This represents the modal dialog box that is reopened when the
        retry button is clicked.
        args: A tuple that contains the positional arguments to be passed to the modal when it is reopened.
        kwargs: A dictionary that contains the keyword arguments to be passed to the modal when it is reopened.
    """
    def __init__(self, modal: Type, *args, **kwargs):
        super().__init__(timeout=None)
        self.modal = modal
        self.args = args
        self.kwargs = kwargs

    @discord.ui.button(label="Réessayer", style=discord.ButtonStyle.primary)
    async def retry(self, _: discord.ui.Button, interaction: discord.Interaction):
        await interaction.response.send_modal(modal=self.modal(*self.args, **self.kwargs))


def get_asset(name: str) -> str:
    return f"https://raw.githubusercontent.com/e-psi-lon/ParalyaBot/main/assets/{name}.webp"