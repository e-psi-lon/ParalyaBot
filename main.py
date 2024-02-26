import logging
import os
import sys
from discord import command
from shared import Users

try:
    import discord
    from discord.ext import commands
    from dotenv import load_dotenv
except ImportError:
    os.system("pip install -r requirements.txt")
    os.execl(sys.executable, sys.executable, *sys.argv)

load_dotenv()
INTENTS = discord.Intents.all()


class Bot(commands.Bot):
    async def on_ready(self):
        logging.info(f"Logged in as {self.user}")

    async def on_application_command_error(self, ctx: discord.ApplicationContext, error: discord.DiscordException):
        if isinstance(error, commands.CommandOnCooldown):
            await ctx.respond(f"Cette commande est en cooldown. Veuillez réessayer dans {error.retry_after:.0f} secondes.",
                              ephemeral=True)
        else:
            logging.error(f"Error in {ctx.command}: {error}")
            embed = discord.Embed(title="Une erreur est survenue", description=f"Erreur provoquée par {ctx.author.mention}",
                                  color=discord.Color.red())
            command = ctx.command
            command_path = []
            while command.parent:
                command_path.append(command.name)
                command = command.parent
            embed.add_field(name="Commande", value=f"`/{''.join(reversed(command_path))}`")
            embed.add_field(name="Module", value=f"`{ctx.command.cog.__class__.__name__!r}`")
            embed.add_field(name="Message d'erreur", value=f"`{error}`")
            embed.add_field(name="Traceback", value=f"```\n{error.__traceback__}```")
            embed.set_footer(text=f"Veuillez transmettre ceci à <@{Users.E_PSI_LON.value}> ou à <@{Users.LUXIO.value}>")
            await ctx.respond(embed=embed, ephemeral=True)
    
    async def on_error(self, event_method: str, *args: logging.Any, **kwargs: logging.Any) -> None:
        # Si dans les kwargs il y a un contexte, on l'envoie dans le on_application_command_error
        if "ctx" in kwargs:
            await self.on_application_command_error(kwargs["ctx"], sys.exc_info())
        else:
            await super().on_error(event_method, *args, **kwargs)

bot = Bot(intents=INTENTS)

if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    for file in os.listdir("games"):
        if file.endswith(".py") and file != "__init__.py":
            bot.load_extension(f"games.{file[:-3]}")
            logging.info(f"Loaded game {file[:-3]}")
    bot.run(os.getenv("TOKEN"))
