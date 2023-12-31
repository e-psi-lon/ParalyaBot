import discord
from shared.utils import *

async def message_callback(self, interaction: discord.Interaction):
    if self.notif:
        message = f"@everyone\n {self.children[0].value}"
    else:
        message = self.children[0].value
    await self.channel.send(message)
    await interaction.response.send_message("Message envoyé !", ephemeral=True)
    